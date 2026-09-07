package com.slothrag.ai.llm;

import java.util.List;

/**
 * 流式回调
 */
public interface StreamCallback {

    /**
     * 增量文本
     */
    void onDelta(String text);

    /**
     * 本轮流结束（工具调用已通过 {@link #onToolCalls} 先送达时，此回调同样会被触发）
     */
    void onComplete();

    /**
     * 本轮模型发起了工具调用（默认无工具，可忽略）
     */
    default void onToolCalls(List<ToolCall> toolCalls) {
    }

    /**
     * 出错
     */
    void onError(Throwable t);
}