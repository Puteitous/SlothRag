package com.ragbase.ai.rerank;

import java.util.List;

/**
 * Rerank 客户端抽象，对候选文档按 query 相关性重排并打分
 */
public interface RerankClient {

    /**
     * @param query       查询
     * @param documents   候选文档（与检索结果一一对应）
     * @param topN        返回前 N 个最相关结果
     * @return 按相关性从高到低排序的结果（index 为 documents 下标）
     */
    List<RerankResult> rerank(String query, List<String> documents, int topN);

    /**
     * Rerank 结果
     *
     * @param index documents 中的下标
     * @param score 相关性分数（0-1）
     */
    record RerankResult(int index, double score) {
    }
}
