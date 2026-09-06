package com.ragbase.ai.llm;

/**
 * 流式回调
 */
public interface StreamCallback {

    /**
     * 增量文本
     */
    void onDelta(String text);

    /**
     * 流结束
     */
    void onComplete();

    /**
     * 出错
     */
    void onError(Throwable t);
}
