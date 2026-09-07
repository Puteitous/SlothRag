package com.slothrag.agent;

import com.slothrag.search.service.SearchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 来源引用（KbSearchTool.toSources）与 SearchResultItem 新字段测试
 */
class KbSearchToolSourceTest {

    @Test
    @DisplayName("toSources 应包含 headingPath 和 fileName")
    void testToSourcesCarriesMeta() {
        var hits = List.of(
                new SearchService.SearchResultItem(1L, 10L, "这是第一段内容。", 0.9, 0.9, 0.5, "第一章 > 1.1 系统架构", "架构设计文档.pdf"),
                new SearchService.SearchResultItem(2L, 10L, "第二段内容。第二段内容。第二段内容。第二段内容。第二段内容。第二段内容。第二段内容。第二段内容。第二段内容。第二段内容。第二段内容。第二段内容。第二段内容。第二段内容。第二段内容。第二段内容。", 0.8, 0.8, 0.4, "第一章 > 1.2 技术选型", "架构设计文档.pdf"),
                new SearchService.SearchResultItem(3L, 11L, "第三段内容。", 0.7, 0.7, 0.3, null, "考勤制度.docx")
        );

        List<Map<String, Object>> sources = KbSearchTool.toSources(hits);

        assertEquals(3, sources.size(), "应有 3 条来源");

        // 第 1 条：有 headingPath + fileName
        Map<String, Object> s0 = sources.get(0);
        assertEquals("这是第一段内容。", s0.get("source"));
        assertEquals("第一章 > 1.1 系统架构", s0.get("headingPath"));
        assertEquals("架构设计文档.pdf", s0.get("fileName"));

        // 第 2 条：长文本应截断到 80 字符
        Map<String, Object> s1 = sources.get(1);
        String src1 = (String) s1.get("source");
        assertTrue(src1.endsWith("…"), "长文本应截断");
        assertTrue(src1.length() <= 81, "截断后不超过 81 字符（80 + …）");
        assertEquals("第一章 > 1.2 技术选型", s1.get("headingPath"));
        assertEquals("架构设计文档.pdf", s1.get("fileName"));

        // 第 3 条：headingPath 为 null → 应返回空字符串
        Map<String, Object> s2 = sources.get(2);
        assertEquals("", s2.get("headingPath"), "null headingPath 应转为空串");
        assertEquals("考勤制度.docx", s2.get("fileName"));
    }

    @Test
    @DisplayName("toSources 空列表/null 应返回空列表")
    void testToSourcesEmpty() {
        assertTrue(KbSearchTool.toSources(null).isEmpty(), "null 输入应返回空列表");
        assertTrue(KbSearchTool.toSources(List.of()).isEmpty(), "空列表输入应返回空列表");
    }

    @Test
    @DisplayName("content 为 null 的命中应跳过")
    void testToSourcesSkipsNullContent() {
        var hits = new ArrayList<SearchService.SearchResultItem>();
        hits.add(new SearchService.SearchResultItem(1L, 10L, null, 0.9, 0.9, 0.5, "第一章", "doc.pdf"));
        hits.add(new SearchService.SearchResultItem(2L, 10L, "正常内容", 0.8, 0.8, 0.4, "第二章", "doc.pdf"));

        List<Map<String, Object>> sources = KbSearchTool.toSources(hits);
        assertEquals(1, sources.size(), "null content 的命中应被跳过");
        assertEquals("正常内容", sources.get(0).get("source"));
    }

    @Test
    @DisplayName("SearchResultItem record 正确携带新字段")
    void testSearchResultItemFields() {
        var item = new SearchService.SearchResultItem(1L, 10L, "内容", 0.9, 0.9, -1.0, "第一章 > 1.1", "报告.pdf");

        assertEquals(1L, item.chunkId());
        assertEquals(10L, item.docId());
        assertEquals("内容", item.content());
        assertEquals(0.9, item.score());
        assertEquals(0.9, item.maxSimilarity());
        assertEquals(-1.0, item.rerankScore());
        assertEquals("第一章 > 1.1", item.headingPath());
        assertEquals("报告.pdf", item.docName());
    }

    @Test
    @DisplayName("SearchResultItem 允许 headingPath/docName 为 null")
    void testSearchResultItemNullFields() {
        var item = new SearchService.SearchResultItem(1L, 10L, "内容", 0.9, 0.9, -1.0, null, null);

        assertNull(item.headingPath(), "headingPath 可为 null");
        assertNull(item.docName(), "docName 可为 null");
    }
}
