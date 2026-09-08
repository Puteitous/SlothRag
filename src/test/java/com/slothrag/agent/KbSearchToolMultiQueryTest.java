package com.slothrag.agent;

import com.slothrag.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * KbSearchTool 多 query 并行检索测试
 */
@ExtendWith(MockitoExtension.class)
class KbSearchToolMultiQueryTest {

    @Mock
    private SearchService searchService;

    private KbSearchTool tool;

    @BeforeEach
    void setUp() {
        tool = new KbSearchTool(searchService);
    }

    // ── 单 query 兼容测试 ───────────────────────────

    @Test
    @DisplayName("execute(String, Long) 单 query 应委托给多 query 版本")
    void testSingleQueryDelegates() {
        when(searchService.search(eq("test"), anyLong()))
                .thenReturn(List.of(
                        new SearchService.SearchResultItem(1L, 10L, "测试内容", 0.9, 0.9, -1.0, "", "")));

        KbSearchTool.ExecResult result = tool.execute("test", 1L);

        assertNotNull(result);
        assertFalse(result.toolContent().isBlank());
        assertEquals(1, result.hits().size());
        verify(searchService, times(1)).search(eq("test"), anyLong());
    }

    // ── 多 query 并行检索测试 ───────────────────────

    @Test
    @DisplayName("execute(List, Long) 多 query 应并行检索并合并结果")
    void testMultiQueryMergesResults() {
        // query1 命中 2 条
        when(searchService.search(eq("年休假规定"), anyLong()))
                .thenReturn(List.of(
                        new SearchService.SearchResultItem(1L, 10L, "年休假天数：5天", 0.92, 0.92, -1.0, "第一章 > 年休假", "hr.pdf"),
                        new SearchService.SearchResultItem(2L, 10L, "年休假申请流程", 0.85, 0.85, -1.0, "第一章 > 年休假", "hr.pdf")));

        // query2 命中 2 条（其中 1 条与 query1 重复 chunkId=1）
        when(searchService.search(eq("春节放假安排"), anyLong()))
                .thenReturn(List.of(
                        new SearchService.SearchResultItem(1L, 10L, "年休假天数：5天", 0.88, 0.88, -1.0, "第一章 > 年休假", "hr.pdf"),
                        new SearchService.SearchResultItem(3L, 11L, "春节放假：10天", 0.95, 0.95, -1.0, "第二章 > 节假日", "hr.pdf")));

        KbSearchTool.ExecResult result = tool.execute(List.of("年休假规定", "春节放假安排"), 1L);

        assertNotNull(result);
        // 3 条不重复（chunkId 1, 2, 3）
        assertEquals(3, result.hits().size(), "应合并去重后为 3 条");

        // 验证去重：保留 maxSimilarity 更高的那条
        // chunkId=1: query1 的 maxSimilarity=0.92, query2 的 0.88 → 应保留 0.92
        var chunk1 = result.hits().stream().filter(h -> h.chunkId() == 1L).findFirst().orElseThrow();
        assertEquals(0.92, chunk1.maxSimilarity(), 0.001, "应保留更高的 maxSimilarity");

        // 验证排序：3, 1, 2（按 maxSimilarity 降序）
        assertEquals(3L, result.hits().get(0).chunkId(), "第一条应为 maxSimilarity 最高的");
        assertEquals(1L, result.hits().get(1).chunkId(), "第二条应为次高的");
        assertEquals(2L, result.hits().get(2).chunkId());

        // 验证 searchService 被调用了 2 次（每个 query 一次）
        verify(searchService, times(2)).search(anyString(), anyLong());
    }

    // ── 边界情况测试 ───────────────────────────────

    @Test
    @DisplayName("queries 为空应返回空结果")
    void testEmptyQueries() {
        KbSearchTool.ExecResult result = tool.execute(List.of(), 1L);
        assertTrue(result.hits().isEmpty());
        assertTrue(result.toolContent().contains("未提供"));
        verifyNoInteractions(searchService);
    }

    @Test
    @DisplayName("queries 为 null 应返回空结果")
    void testNullQueries() {
        KbSearchTool.ExecResult result = tool.execute((List<String>) null, 1L);
        assertTrue(result.hits().isEmpty());
        assertTrue(result.toolContent().contains("未提供"));
        verifyNoInteractions(searchService);
    }

    @Test
    @DisplayName("部分 query 检索失败不应影响其他 query 的结果")
    void testPartialFailure() {
        // query1 成功
        when(searchService.search(eq("成功查询"), anyLong()))
                .thenReturn(List.of(
                        new SearchService.SearchResultItem(1L, 10L, "成功内容", 0.9, 0.9, -1.0, "", "")));

        // query2 抛出异常
        when(searchService.search(eq("失败查询"), anyLong()))
                .thenThrow(new RuntimeException("模拟检索失败"));

        KbSearchTool.ExecResult result = tool.execute(List.of("成功查询", "失败查询"), 1L);

        assertNotNull(result);
        assertEquals(1, result.hits().size(), "失败查询应被跳过，只保留成功的");
        assertEquals("成功内容", result.hits().get(0).content());

        verify(searchService, times(1)).search(eq("成功查询"), anyLong());
        verify(searchService, times(1)).search(eq("失败查询"), anyLong());
    }

    @Test
    @DisplayName("所有 query 都失败应返回空结果")
    void testAllFailures() {
        when(searchService.search(anyString(), anyLong()))
                .thenThrow(new RuntimeException("模拟检索失败"));

        KbSearchTool.ExecResult result = tool.execute(List.of("q1", "q2"), 1L);

        assertTrue(result.hits().isEmpty());
        assertTrue(result.toolContent().contains("未检索到"));
    }
}
