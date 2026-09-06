package com.ragbase.ai.embedding;

import java.util.List;

/**
 * Embedding 客户端抽象，可切换不同供应商实现
 */
public interface EmbeddingClient {

    /**
     * 对文本列表批量向量化，返回顺序与入参一致
     */
    List<float[]> embed(List<String> texts);

    /**
     * 向量维度
     */
    int dimension();
}
