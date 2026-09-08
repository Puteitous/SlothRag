package com.slothrag.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slothrag.agent.GrepDocTool;
import com.slothrag.agent.KbSearchTool;
import com.slothrag.agent.ReadDocTool;
import com.slothrag.ai.llm.ChatMessage;
import com.slothrag.ai.llm.OpenAiCompatibleLlmClient;
import com.slothrag.ai.llm.StreamCallback;
import com.slothrag.ai.llm.ToolCall;
import com.slothrag.ai.llm.ToolDefinition;
import com.slothrag.conversation.ConversationService;
import com.slothrag.session.SessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 问答服务（简版 Agentic）：模型自主决定是否调用 {@code search_kb} 工具检索，
 * 检索结果作为 tool 消息回喂后再生成最终回答；闲聊/无关问题模型不调用工具直接答。
 * 边界由 system prompt 约束（只答知识库范围内问题），无证据时由模型按提示拒答。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 代理边界提示词：约束模型只在需要知识时检索、其余按类型自判，防越界/防编造 */
    private static final String AGENT_SYSTEM_PROMPT =
            "你是企业知识库问答助手。请依据知识库内容回答企业相关问题。\n"
            + "行为规则：\n"
            + "1. 只有当问题属于知识库范围、需要知识支撑时才调用 search_kb。\n"
            + "2. 打招呼 / 闲聊（如“你好”“谢谢”“在吗”）：不要调用工具，直接用一两句话礼貌回应即可。\n"
            + "3. 明显与知识库业务无关的通用问题（时事、娱乐、生活等）：不要调用工具，礼貌说明你只服务知识库范围内的企业问题，不展开。\n"
            + "4. 调用检索后有充分依据：严格基于检索内容回答，不编造，可在相关结论后标注【来源】。\n"
            + "5. 如果一轮检索结果不够充分（缺少关键信息），可以调整关键词再次调用 search_kb 补充检索。\n"
            + "6. 调用检索后仍未检索到相关依据：明确告知用户“该问题暂未收录到知识库”，并可提示联系人工。\n"
            + "回答保持简洁、自然、友好。";

    /** agent 工具调用最大轮次（防止无限循环） */
    private static final int MAX_TOOL_TURNS = 4;

    /** SSE 增量缓冲：时间窗（毫秒）与字数阈值，避免逐字事件 */
    private static final long FLUSH_INTERVAL_MS = 80;
    private static final int FLUSH_THRESHOLD = 50;

    /** 共享的缓冲刷新调度器（daemon） */
    private static final ScheduledExecutorService FLUSH_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-flush");
                t.setDaemon(true);
                return t;
            });

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenAiCompatibleLlmClient llmClient;
    private final SessionStore sessionStore;
    private final ConversationService conversationService;
    private final KbSearchTool kbSearchTool;
    private final ReadDocTool readDocTool;
    private final GrepDocTool grepDocTool;
    private final RecommendedQuestionService recommendedQuestionService;

    /**
     * 流式问答（agent loop，支持多轮）
     *
     * @param conversationId 已有会话 id；为空时新建会话，sessionId 通过 SSE session 事件返回
     */
    public void streamChat(String question, Long kbId, String conversationId, SseEmitter emitter) {
        try {
            String sessionId = StringUtils.hasText(conversationId) ? conversationId : sessionStore.newSessionId();
            // 会话元数据登录：首问建会话（title=首问），续接滚动 updated_at
            conversationService.upsert(sessionId, question);
            emitter.send(SseEmitter.event().name("session").data(sessionId));

            // 历史全量加载（稳定前缀）+ 追加本轮问题
            List<ChatMessage> history = sessionStore.loadMessages(sessionId);
            sessionStore.appendMessage(sessionId, ChatMessage.user(question));

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(AGENT_SYSTEM_PROMPT));
            messages.addAll(history);
            messages.add(ChatMessage.user(question));

            List<ToolDefinition> tools = List.of(kbSearchTool.definition(),
                    readDocTool.definition(), grepDocTool.definition());
            List<Map<String, Object>> sourcesAcc = new ArrayList<>();

            DeltaBuffer buffer = new DeltaBuffer(emitter);
            buffer.start();
            runLoop(messages, tools, kbId, sessionId, emitter, buffer, sourcesAcc, 0, question);
        } catch (Exception e) {
            log.error("问答异常", e);
            sendError(emitter, e);
        }
    }

    /**
     * agent 主循环：每轮流式调用模型；若模型发起工具调用则执行、回喂上下文后递归下一轮，
     * 否则把本轮流式内容作为最终回答落盘、发送 sources 后结束。
     */
    private void runLoop(List<ChatMessage> messages, List<ToolDefinition> tools, Long kbId,
                         String sessionId, SseEmitter emitter, DeltaBuffer buffer,
                         List<Map<String, Object>> sourcesAcc, int turn, String question) {
        if (turn >= MAX_TOOL_TURNS) {
            buffer.stop();
            complete(emitter);
            return;
        }

        StringBuilder turnText = new StringBuilder();
        boolean[] executed = {false};

        llmClient.streamChat(messages, tools, new StreamCallback() {
            @Override
            public void onDelta(String text) {
                turnText.append(text);
                buffer.append(text);
            }

            @Override
            public void onToolCalls(List<ToolCall> calls) {
                if (executed[0]) {
                    return;
                }
                executed[0] = true;
                try {
                    runTools(calls, messages, kbId, sourcesAcc);
                    runLoop(messages, tools, kbId, sessionId, emitter, buffer, sourcesAcc, turn + 1, question);
                } catch (Exception e) {
                    log.error("工具执行失败", e);
                    buffer.stop();
                    sendError(emitter, e);
                }
            }

            @Override
            public void onComplete() {
                if (executed[0]) {
                    return; // 工具轮已由下一轮接管
                }
                // 最终回答轮：落盘该轮完整文本，发送 sources 后结束
                buffer.stop();
                String answer = turnText.toString();
                if (StringUtils.hasText(answer)) {
                    sessionStore.appendMessage(sessionId, ChatMessage.assistant(answer));
                }
                try {
                    if (!sourcesAcc.isEmpty()) {
                        emitter.send(SseEmitter.event().name("sources").data(sourcesAcc));
                    }
                    // 推荐问题：用非流式轻量 LLM 调用生成 3 个相关问题
                    List<String> recommended = recommendedQuestionService.generate(question, answer);
                    if (!recommended.isEmpty()) {
                        emitter.send(SseEmitter.event().name("recommended").data(recommended));
                    }
                } catch (IOException e) {
                    log.debug("SSE 发送失败: {}", e.getMessage());
                }
                complete(emitter);
            }

            @Override
            public void onError(Throwable t) {
                buffer.stop();
                sendError(emitter, t);
            }
        });
    }

    /**
     * 执行本轮全部工具调用：写入 assistant(tool_calls) + 每条 tool 结果，并收集来源
     */
    private void runTools(List<ToolCall> calls, List<ChatMessage> messages, Long kbId,
                          List<Map<String, Object>> sourcesAcc) {
        List<ToolCall> known = calls.stream()
                .filter(t -> t.getFunction() != null)
                .toList();
        if (known.isEmpty()) {
            return;
        }
        messages.add(ChatMessage.assistantToolCall(known));

        for (ToolCall call : known) {
            String name = call.getFunction().getName();
            String result = executeTool(name, call.getFunction().getArguments(), kbId, sourcesAcc);
            messages.add(ChatMessage.toolResult(call.getId(), name, result));
        }
    }

    /**
     * 根据工具名称分发执行
     */
    private String executeTool(String name, String arguments, Long kbId,
                               List<Map<String, Object>> sourcesAcc) {
        return switch (name) {
            case KbSearchTool.NAME -> {
                List<String> queries = parseQueries(arguments);
                KbSearchTool.ExecResult r = kbSearchTool.execute(queries, kbId);
                sourcesAcc.addAll(KbSearchTool.toSources(r.hits()));
                yield r.toolContent();
            }
            case ReadDocTool.NAME -> {
                try {
                    JsonNode node = MAPPER.readTree(arguments);
                    Long docId = node.path("doc_id").asLong(0);
                    int seqStart = node.path("seq_start").asInt(1);
                    int seqEnd = node.path("seq_end").asInt(0);
                    yield readDocTool.execute(docId,
                            node.has("seq_start") ? seqStart : null,
                            node.has("seq_end") ? seqEnd : null);
                } catch (IOException e) {
                    yield "参数解析失败: " + e.getMessage();
                }
            }
            case GrepDocTool.NAME -> {
                try {
                    JsonNode node = MAPPER.readTree(arguments);
                    Long docId = node.path("doc_id").asLong(0);
                    String keyword = node.path("keyword").asText("");
                    yield grepDocTool.execute(docId, keyword);
                } catch (IOException e) {
                    yield "参数解析失败: " + e.getMessage();
                }
            }
            default -> "未知工具：" + name;
        };
    }

    /**
     * 解析工具参数中的 queries（数组）；回退尝试解析单 query（兼容旧模型）。
     * 解析失败或为空时返回含空串的列表（由检索层兜底）。
     */
    private List<String> parseQueries(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return List.of("");
        }
        try {
            JsonNode node = MAPPER.readTree(arguments);
            JsonNode queries = node.path("queries");
            if (queries.isArray() && queries.size() > 0) {
                List<String> result = new ArrayList<>();
                for (JsonNode q : queries) {
                    String text = q.asText().trim();
                    if (!text.isEmpty()) {
                        result.add(text);
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
            // 回退：解析单 query（兼容旧模型 tool definition）
            String single = node.path("query").asText("");
            if (!single.isEmpty()) {
                return List.of(single);
            }
            return List.of("");
        } catch (IOException e) {
            return List.of("");
        }
    }

    private void complete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE complete 失败: {}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, Throwable t) {
        try {
            emitter.send(SseEmitter.event().name("error").data(t.getMessage()));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(t);
        }
    }

    /**
     * SSE 增量批量缓冲：按时间窗/字数阈值聚合发送，结束时强制刷出剩余内容
     */
    private static final class DeltaBuffer {

        private final SseEmitter emitter;
        private final StringBuilder buf = new StringBuilder();
        private ScheduledFuture<?> task;

        DeltaBuffer(SseEmitter emitter) {
            this.emitter = emitter;
        }

        void start() {
            task = FLUSH_SCHEDULER.scheduleAtFixedRate(this::flushIfDue,
                    FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }

        synchronized void append(String text) {
            buf.append(text);
            if (buf.length() >= FLUSH_THRESHOLD) {
                flush();
            }
        }

        synchronized void flushIfDue() {
            if (buf.length() > 0) {
                flush();
            }
        }

        void stop() {
            cancelTask();
            synchronized (this) {
                flush();
            }
        }

        private void flush() {
            if (buf.length() == 0) {
                return;
            }
            String chunk = buf.toString();
            buf.setLength(0);
            try {
                emitter.send(SseEmitter.event().name("delta").data(chunk));
            } catch (IOException e) {
                log.debug("SSE 发送中断: {}", e.getMessage());
                cancelTask();
            }
        }

        private void cancelTask() {
            if (task != null) {
                task.cancel(false);
            }
        }
    }
}