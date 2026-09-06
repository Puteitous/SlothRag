package com.ragbase.chat.service;

import com.ragbase.ai.llm.ChatMessage;
import com.ragbase.ai.llm.OpenAiCompatibleLlmClient;
import com.ragbase.ai.llm.StreamCallback;
import com.ragbase.config.ChatProperties;
import com.ragbase.config.SearchProperties;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String SYSTEM_PROMPT =
            "你是一个基于知识库回答问题的助手。请严格依据提供的知识内容回答，不要编造或添加知识中没有的信息。" +
            "若知识内容中没有任何依据可以回答该问题，你必须原样回答【很抱歉，这个问题超出了当前知识范围，建议联系人工客服（转8001）进行咨询。】，禁止猜测或编造。" +
            "回答时可在相关结论后标注来源编号，如【来源1】。";

    /** 被拒问题的答案落盘文案（与配置话术保持一致性） */
    private static final String REJECTED_ANSWER = "抱歉，该问题超出当前知识范围。";

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
            emitter.send(SseEmitter.event().name("session").data(sessionId));

            // 历史全量加载（稳定前缀）+ 追加本轮问题
            List<ChatMessage> history = sessionStore.loadMessages(sessionId);
            sessionStore.appendMessage(sessionId, ChatMessage.user(question));

            List<SearchService.SearchResultItem> hits = searchService.search(question, kbId);

            // 边界处理（证据闸门）：空结果，或整批最高分低于阈值 → 拒绝回答
            // 判据优先用 Rerank 分（更准），未打分时回退到向量相似度
            if (hits.isEmpty() || belowGate(hits)) {
                sessionStore.appendMessage(sessionId, ChatMessage.assistant(REJECTED_ANSWER));
                emitter.send(SseEmitter.event().name("delta").data(chatProperties.getOutOfScopeMessage()));
                emitter.complete();
                return;
            }

            String context = buildContext(hits);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(SYSTEM_PROMPT));
            messages.addAll(history); // 多轮历史全量进 prompt
            messages.add(ChatMessage.user("【知识内容】\n" + context + "\n\n【问题】" + question));

            // 来源引用（用于回答结束后的 sources 事件）
            List<Map<String, Object>> sources = hits.stream()
                    .map(h -> Map.<String, Object>of(
                            "source", h.content().length() > 80 ? h.content().substring(0, 80) + "…" : h.content()))
                    .toList();

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
                        emitter.send(SseEmitter.event().name("sources").data(sources));
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

    /**
     * 证据闸门：整批最高分低于阈值则拒绝
     * 有 Rerank 分时用 minRerankScore（判据更准），否则回退 minSimilarity
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
}
