package com.ragbase.chat.service;

import com.ragbase.ai.llm.ChatMessage;
import com.ragbase.ai.llm.OpenAiCompatibleLlmClient;
import com.ragbase.ai.llm.StreamCallback;
import com.ragbase.config.ChatProperties;
import com.ragbase.config.SearchProperties;
import com.ragbase.conversation.ConversationService;
import com.ragbase.search.service.SearchService;
import com.ragbase.session.SessionStore;
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
 * 问答服务：检索 → 组装上下文 → LLM 流式回答（SSE）
 * <p>
 * 证据判定策略（参考 ragent）：不再用检索分数硬拒。检索无证据时放行给 LLM，
 * 由 system prompt 按问题类型引导模型自行判断（闲聊友好答 / 库内无依据按话术拒）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 有检索上下文时的强约束提示 */
    private static final String SYSTEM_PROMPT_WITH_CONTEXT =
            "你是一个基于知识库回答问题的助手。请严格依据提供的知识内容回答，不要编造或添加知识中没有的信息。" +
            "回答时可在相关结论后标注来源编号，如【来源1】。";

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

    private final SearchService searchService;
    private final OpenAiCompatibleLlmClient llmClient;
    private final SessionStore sessionStore;
    private final ConversationService conversationService;
    private final ChatProperties chatProperties;
    private final SearchProperties searchProperties;

    /**
     * 流式问答（支持多轮）
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

            List<SearchService.SearchResultItem> hits = searchService.search(question, kbId);

            // 无检索证据（空结果或整批最高分低于阈值）：放行给 LLM 自判，而非硬拒
            boolean gated = hits.isEmpty() || belowGate(hits);

            List<ChatMessage> messages = gated
                    ? buildMessages(question, history, null)
                    : buildMessages(question, history, buildContext(hits));

            List<Map<String, Object>> sources = gated
                    ? List.of()
                    : hits.stream()
                            .map(h -> Map.<String, Object>of(
                                    "source", h.content().length() > 80 ? h.content().substring(0, 80) + "…" : h.content()))
                            .toList();

            streamAnswer(emitter, sessionId, messages, sources);
        } catch (Exception e) {
            log.error("问答异常", e);
            try {
                emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(e);
            }
        }
    }

    /**
     * 组装消息：有上下文则注入知识内容强约束回答；无上下文则仅传问题，
     * 由分类 system prompt 引导模型判断该问题属于哪种类型并相应作答。
     */
    private List<ChatMessage> buildMessages(String question, List<ChatMessage> history, String context) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(systemPrompt(context != null)));
        messages.addAll(history); // 多轮历史全量进 prompt
        String userContent = context == null
                ? question
                : "【知识内容】\n" + context + "\n\n【问题】" + question;
        messages.add(ChatMessage.user(userContent));
        return messages;
    }

    /**
     * 分类 system prompt：无检索证据时让模型按问题类型自行判断，避免"你好"也被拒答
     */
    private String systemPrompt(boolean hasContext) {
        if (hasContext) {
            return SYSTEM_PROMPT_WITH_CONTEXT;
        }
        return "你是一个企业知识库问答助手。当前从知识库没能检索到相关内容，请先判断用户问题的类型再作答：\n"
                + "1. 打招呼 / 闲聊（如“你好”“在吗”“谢谢”）：简短、友好地回应即可。\n"
                + "2. 关于你自身的问题（如“你是谁”“你能做什么”）：简要介绍你是基于企业知识库的问答助手。\n"
                + "3. 明显与知识库无关的通用问题：礼貌说明你主要服务于知识库范围内的问题。\n"
                + "4. 属于知识库领域、但知识库暂未收录依据的问题：请务必原样回答“"
                + chatProperties.getOutOfScopeMessage() + "”，不要猜测或编造。\n"
                + "整体保持简洁、自然、友好。";
    }

    /**
     * 统一的流式回答执行：增量缓冲、落盘、按需发送 sources、结束时 complete。
     */
    private void streamAnswer(SseEmitter emitter, String sessionId,
                              List<ChatMessage> messages, List<Map<String, Object>> sources) {
        DeltaBuffer buffer = new DeltaBuffer(emitter);
        buffer.start();
        StringBuilder fullAnswer = new StringBuilder();
        llmClient.streamChat(messages, new StreamCallback() {
            @Override
            public void onDelta(String text) {
                buffer.append(text);
                fullAnswer.append(text);
            }

            @Override
            public void onComplete() {
                buffer.stop();
                sessionStore.appendMessage(sessionId, ChatMessage.assistant(fullAnswer.toString()));
                try {
                    if (!sources.isEmpty()) {
                        emitter.send(SseEmitter.event().name("sources").data(sources));
                    }
                    emitter.complete();
                } catch (IOException e) {
                    log.debug("SSE 发送 sources 失败: {}", e.getMessage());
                }
            }

            @Override
            public void onError(Throwable t) {
                buffer.stop();
                try {
                    emitter.send(SseEmitter.event().name("error").data(t.getMessage()));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(t);
                }
            }
        });
    }

    /**
     * 证据闸门：整批最高分低于阈值则视为无证据
     * 有 Rerank 分时用 minRerankScore（判据更准），否则回退到向量相似度
     */
    private boolean belowGate(List<SearchService.SearchResultItem> hits) {
        boolean hasRerankScore = hits.stream()
                .anyMatch(h -> h.rerankScore() > SearchService.NO_RERANK_SCORE);
        double gateScore;
        double threshold;
        if (hasRerankScore) {
            gateScore = hits.stream()
                    .mapToDouble(SearchService.SearchResultItem::rerankScore).max().orElse(0);
            threshold = searchProperties.getMinRerankScore();
        } else {
            gateScore = hits.stream()
                    .mapToDouble(SearchService.SearchResultItem::maxSimilarity).max().orElse(0);
            threshold = chatProperties.getMinSimilarity();
        }
        return gateScore < threshold;
    }

    private String buildContext(List<SearchService.SearchResultItem> hits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            sb.append("【来源").append(i + 1).append("】\n")
                    .append(hits.get(i).content())
                    .append("\n\n");
        }
        return sb.toString();
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
                // 客户端已断开，停止后续刷新
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