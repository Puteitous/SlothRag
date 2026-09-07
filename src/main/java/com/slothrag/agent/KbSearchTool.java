package com.slothrag.agent;

import com.slothrag.ai.llm.ToolDefinition;
import com.slothrag.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索工具（`search_kb`），暴露给大模型自主调用
 */
@Service
@RequiredArgsConstructor
public class KbSearchTool {

    public static final String NAME = "search_kb";

    private static final String DESCRIPTION =
            "在企业知识库中检索与用户问题相关的文档片段。当问题需要依据知识库内容回答时调用。";

    private static final Map<String, Object> PARAMETERS = Map.of(
            "type", "object",
            "properties", Map.of(
                    "query", Map.of("type", "string",
                            "description", "需要在知识库中检索的关键内容或完整问题")),
            "required", List.of("query"));

    private static final ToolDefinition DEFINITION =
            new ToolDefinition(NAME, DESCRIPTION, PARAMETERS);

    private final SearchService searchService;

    public ToolDefinition definition() {
        return DEFINITION;
    }

    /**
     * 执行检索，返回用于回传给模型的工具结果文本 + 原始命中（供来源展示）
     */
    public ExecResult execute(String query, Long kbId) {
        List<SearchService.SearchResultItem> hits = searchService.search(query, kbId);
        StringBuilder content = new StringBuilder();
        if (hits.isEmpty()) {
            content.append("未检索到任何相关内容。");
        } else {
            for (int i = 0; i < hits.size(); i++) {
                content.append("【结果").append(i + 1).append("】\n")
                        .append(hits.get(i).content())
                        .append("\n\n");
            }
        }
        return new ExecResult(content.toString(), hits);
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
            sources.add(Map.of("source", c.length() > 80 ? c.substring(0, 80) + "…" : c));
        }
        return sources;
    }

    /**
     * 工具执行结果
     */
    public record ExecResult(String toolContent, List<SearchService.SearchResultItem> hits) {
    }
}