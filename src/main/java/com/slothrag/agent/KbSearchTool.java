package com.slothrag.agent;

import com.slothrag.ai.llm.ToolDefinition;
import com.slothrag.common.logging.LoggingContext;
import com.slothrag.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 知识库检索工具（`search_kb`），暴露给大模型自主调用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbSearchTool {

    public static final String NAME = "search_kb";

    private static final String DESCRIPTION =
            "在企业知识库中检索与用户问题相关的文档片段。当问题需要依据知识库内容回答时调用。\n"
            + "对于复杂问题，可以同时提交多个不同的搜索关键词，从不同角度检索以提高覆盖率。";

    private static final Map<String, Object> PARAMETERS = Map.of(
            "type", "object",
            "properties", Map.of(
                    "queries", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string"),
                            "description", "需要在知识库中检索的关键内容或完整问题列表。可以提交多个关键词从不同角度检索。")),
            "required", List.of("queries"));

    private static final ToolDefinition DEFINITION =
            new ToolDefinition(NAME, DESCRIPTION, PARAMETERS);

    private final SearchService searchService;

    public ToolDefinition definition() {
        return DEFINITION;
    }

    /**
     * 执行检索（单 query，兼容旧调用）
     */
    public ExecResult execute(String query, Long kbId) {
        return execute(List.of(query), kbId);
    }

    /**
     * 执行检索（多 query 并行）：每个 query 并发调用 SearchService，
     * 合并去重后返回，结果按 maxSimilarity 降序排列。
     */
    public ExecResult execute(List<String> queries, Long kbId) {
        if (queries == null || queries.isEmpty()) {
            return new ExecResult("未提供查询关键词。", List.of());
        }

        // 当前线程（LLM 回调线程）已挂 sessionId，快照传给并行子线程，避免检索日志丢上下文
        Map<String, String> ctx = LoggingContext.snapshot();

        // 并行检索每个 query
        List<CompletableFuture<List<SearchService.SearchResultItem>>> futures = queries.stream()
                .map(q -> CompletableFuture.supplyAsync(() -> {
                    try (var ignored = LoggingContext.with(ctx)) {
                        try {
                            return searchService.search(q, kbId);
                        } catch (Exception e) {
                            log.warn("检索失败 query='{}': {}", q, e.getMessage());
                            return List.<SearchService.SearchResultItem>of();
                        }
                    }
                }))
                .toList();

        // 合并结果，按 chunkId 去重（保留最高分）
        Map<Long, SearchService.SearchResultItem> merged = new java.util.LinkedHashMap<>();
        for (var future : futures) {
            for (SearchService.SearchResultItem hit : future.join()) {
                merged.merge(hit.chunkId(), hit, (a, b) ->
                        a.maxSimilarity() >= b.maxSimilarity() ? a : b);
            }
        }

        List<SearchService.SearchResultItem> allHits = merged.values().stream()
                .sorted(Comparator.comparingDouble(SearchService.SearchResultItem::maxSimilarity).reversed())
                .toList();

        log.info("多查询检索完成 queries={} 合并后命中={}", queries, allHits.size());

        // 组装工具结果文本
        StringBuilder content = new StringBuilder();
        if (allHits.isEmpty()) {
            content.append("未检索到任何相关内容。");
        } else {
            for (int i = 0; i < allHits.size(); i++) {
                content.append("【结果").append(i + 1).append("】\n")
                        .append(allHits.get(i).content())
                        .append("\n\n");
            }
        }
        return new ExecResult(content.toString(), allHits);
    }

    /**
     * 把命中片段转成前端的来源引用列表
     */
    public static List<Map<String, Object>> toSources(List<SearchService.SearchResultItem> hits) {
        if (hits == null) {
            return List.of();
        }
        List<Map<String, Object>> sources = new ArrayList<>();
        for (SearchService.SearchResultItem hit : hits) {
            String c = hit.content();
            if (c == null) {
                continue;
            }
            sources.add(Map.of(
                    "source", c.length() > 80 ? c.substring(0, 80) + "…" : c,
                    "headingPath", hit.headingPath() != null ? hit.headingPath() : "",
                    "fileName", hit.docName() != null ? hit.docName() : ""));
        }
        return sources;
    }

    /**
     * 工具执行结果
     */
    public record ExecResult(String toolContent, List<SearchService.SearchResultItem> hits) {
    }
}