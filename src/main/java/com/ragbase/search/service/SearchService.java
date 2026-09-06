package com.ragbase.search.service;

import com.ragbase.ai.embedding.EmbeddingClient;
import com.ragbase.ai.rerank.RerankClient;
import com.ragbase.config.SearchProperties;
import com.ragbase.knowledge.dao.ChunkDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 检索服务：向量 + 关键词双通道并行召回，RRF 融合，Rerank 重排出分
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int VECTOR_TOP_K = 8;
    private static final int KEYWORD_TOP_K = 8;
    private static final double RRF_K = 60.0;
    /** 关键词 LIKE 命中的语义相似度置高值，视为强相关信号 */
    private static final double STRONG_SIGNAL_SIMILARITY = 0.8;
    /** 未做 Rerank 时 rerankScore 占位值 */
    public static final double NO_RERANK_SCORE = -1.0;
    /** 中文停用词（单字） */
    private static final String STOP_CHARS = "的了吗呢吧啊有是在我你他它下为与和及或这个那请问哪个";
    /** 中文停用词（双字） */
    private static final java.util.Set<String> STOP_WORDS_2 =
            java.util.Set.of("怎么", "什么", "多少", "为何", "如何", "为啥", "哪里", "怎样", "几天", "多久", "一下", "可以", "时候");

    private final ChunkDao chunkDao;
    private final EmbeddingClient embeddingClient;
    private final RerankClient rerankClient;
    private final SearchProperties searchProperties;

    /**
     * 检索结果项
     *
     * @param score         RRF 融合分（用于融合排序）
     * @param maxSimilarity 最高语义相似度（关键词 LIKE 命中视为强信号）
     * @param rerankScore   Rerank 相关性分；未打分时为 {@link #NO_RERANK_SCORE}
     */
    public record SearchResultItem(Long chunkId, Long docId, String content, double score, double maxSimilarity, double rerankScore) {
    }

    /**
     * 双通道检索 + RRF 融合 + Rerank 重排
     */
    public List<SearchResultItem> search(String question, Long kbId) {
        List<ChunkDao.SearchHit> vectorHits = List.of();
        List<ChunkDao.SearchHit> keywordHits = List.of();
        try {
            float[] queryVector = embeddingClient.embed(List.of(question)).get(0);
            vectorHits = chunkDao.vectorSearch(queryVector, kbId, VECTOR_TOP_K);
        } catch (Exception e) {
            log.warn("向量检索失败，降级为仅关键词: {}", e.getMessage());
        }
        keywordHits = chunkDao.keywordSearch(extractKeywords(question), kbId, KEYWORD_TOP_K);
        log.info("search stage=retrieve question={} vectorHits={} keywordHits={}", question, vectorHits.size(), keywordHits.size());

        // RRF 融合出候选（rerankCandidates 条，供 Rerank 精排）
        List<SearchResultItem> candidates = fuse(vectorHits, keywordHits);
        log.info("search stage=fuse candidates={}", candidates.size());
        if (candidates.isEmpty()) {
            return List.of();
        }

        if (searchProperties.isRerankEnabled()) {
            List<SearchResultItem> reranked = rerank(candidates, question);
            if (reranked != null) {
                return reranked;
            }
            log.warn("Rerank 不可用，回退到 RRF 排序结果");
        }
        // 未启用或 Rerank 失败：取融合排序前 contextTopK
        return candidates.stream().limit(searchProperties.getContextTopK()).toList();
    }

    /**
     * 对候选做 Rerank 重排并附分；异常返回 null（由调用方回退）
     */
    private List<SearchResultItem> rerank(List<SearchResultItem> candidates, String question) {
        try {
            List<RerankClient.RerankResult> reranked = rerankClient.rerank(
                    question,
                    candidates.stream().map(SearchResultItem::content).toList(),
                    searchProperties.getContextTopK());
            log.info("search stage=rerank results={} scores={}", reranked.size(),
                    reranked.stream().map(r -> String.format("%.3f", r.score())).toList());

            List<SearchResultItem> result = new ArrayList<>(reranked.size());
            for (RerankClient.RerankResult rr : reranked) {
                SearchResultItem item = candidates.get(rr.index());
                result.add(new SearchResultItem(item.chunkId(), item.docId(), item.content(),
                        item.score(), item.maxSimilarity(), rr.score()));
            }
            return result;
        } catch (Exception e) {
            log.warn("Rerank 调用失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * RRF（Reciprocal Rank Fusion）融合：
     * score = Σ 1/(k + rank)，两个通道排名越靠前的命中分数越高
     */
    private List<SearchResultItem> fuse(List<ChunkDao.SearchHit> vectorHits,
                                        List<ChunkDao.SearchHit> keywordHits) {
        Map<Long, SearchResultItem> merged = new HashMap<>();
        rank(vectorHits, merged, false);
        rank(keywordHits, merged, true);

        return merged.values().stream()
                .sorted(Comparator.comparingDouble(SearchResultItem::score).reversed())
                .limit(searchProperties.getRerankCandidates())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 中文关键词粗切：按停用词/标点切段，保留长度 ≥ 2 的片段
     * MVP 启发式方案，后续可换正式分词（pg_jieba / zhparser）
     */
    List<String> extractKeywords(String question) {
        String s = question.replaceAll("[\\p{P}\\p{S}\\s]", "");
        List<String> segments = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (i + 1 < s.length()) {
                String two = s.substring(i, i + 2);
                if (STOP_WORDS_2.contains(two)) {
                    flushSegment(cur, segments);
                    i += 2;
                    continue;
                }
            }
            char ch = s.charAt(i);
            if (STOP_CHARS.indexOf(ch) >= 0) {
                flushSegment(cur, segments);
            } else {
                cur.append(ch);
            }
            i++;
        }
        flushSegment(cur, segments);
        return segments;
    }

    private void flushSegment(StringBuilder cur, List<String> segments) {
        if (cur.length() >= 2) {
            segments.add(cur.toString());
        }
        cur.setLength(0);
    }

    /**
     * @param strongSignal 关键词 LIKE 命中视为强信号（精确子串匹配，语义相似度置高值）
     */
    private void rank(List<ChunkDao.SearchHit> hits, Map<Long, SearchResultItem> merged, boolean strongSignal) {
        for (int i = 0; i < hits.size(); i++) {
            ChunkDao.SearchHit hit = hits.get(i);
            double rrfScore = 1.0 / (RRF_K + i + 1);
            double similarity = strongSignal ? Math.max(hit.score(), STRONG_SIGNAL_SIMILARITY) : hit.score();
            merged.merge(hit.id(),
                    new SearchResultItem(hit.id(), hit.docId(), hit.content(), rrfScore, similarity, NO_RERANK_SCORE),
                    (old, cur) -> new SearchResultItem(
                            old.chunkId(), old.docId(), old.content(),
                            old.score() + cur.score(),
                            Math.max(old.maxSimilarity(), cur.maxSimilarity()),
                            NO_RERANK_SCORE));
        }
    }
}
