package com.slothrag.agent;

import com.slothrag.knowledge.dao.ChunkDao;
import com.slothrag.knowledge.domain.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GrepDocTool 单元测试
 */
@ExtendWith(MockitoExtension.class)
class GrepDocToolTest {

    @Mock
    private ChunkDao chunkDao;

    private GrepDocTool tool;

    @BeforeEach
    void setUp() {
        tool = new GrepDocTool(chunkDao);
    }

    // ── 正常搜索测试 ───────────────────────────────

    @Test
    @DisplayName("搜索关键词应返回匹配的格式化内容")
    void testGrepNormal() {
        when(chunkDao.grepDoc(eq(10L), eq("年休假"))).thenReturn(List.of(
                createChunk(1, "年休假天数：5天", "第一章 > 年休假"),
                createChunk(3, "年休假申请流程", "第一章 > 年休假")));

        String result = tool.execute(10L, "年休假");

        assertTrue(result.contains("年休假"), "结果应包含搜索词");
        assertTrue(result.contains("切片 1"), "应显示切片序号");
        assertTrue(result.contains("年休假天数：5天"), "应包含切片内容");
        assertTrue(result.contains("切片 3"), "应显示第二个切片");
        assertTrue(result.contains("年休假申请流程"));
        assertTrue(result.contains("第一章 > 年休假"), "应显示章节路径");
        assertTrue(result.contains("2 处匹配"), "应显示匹配数量");

        verify(chunkDao, times(1)).grepDoc(eq(10L), eq("年休假"));
    }

    // ── 空结果测试 ─────────────────────────────────

    @Test
    @DisplayName("没有匹配结果应返回提示信息")
    void testNoMatch() {
        when(chunkDao.grepDoc(anyLong(), anyString())).thenReturn(List.of());

        String result = tool.execute(10L, "不存在的词");

        assertTrue(result.contains("没有找到"));
        assertTrue(result.contains("不存在的词"));
        assertTrue(result.contains("10"), "应显示文档 ID");
    }

    // ── 关键字为空测试 ─────────────────────────────

    @Test
    @DisplayName("keyword 为空应返回错误提示")
    void testEmptyKeyword() {
        String result = tool.execute(10L, "");

        assertTrue(result.contains("不能为空"));
        verifyNoInteractions(chunkDao);
    }

    @Test
    @DisplayName("keyword 为纯空格应返回错误提示")
    void testBlankKeyword() {
        String result = tool.execute(10L, "   ");

        assertTrue(result.contains("不能为空"));
        verifyNoInteractions(chunkDao);
    }

    // ── 无 headingPath 测试 ────────────────────────

    @Test
    @DisplayName("headingPath 为 null 的匹配项不应显示章节信息")
    void testNullHeadingPath() {
        when(chunkDao.grepDoc(anyLong(), anyString())).thenReturn(List.of(
                createChunk(1, "匹配内容", null)));

        String result = tool.execute(10L, "匹配");

        assertTrue(result.contains("匹配内容"));
        assertFalse(result.contains("章节："));
    }

    // ── 查找结果数量限制测试 ────────────────────────

    @Test
    @DisplayName("超过 MAX_RESULTS(30) 条时应截断")
    void testMaxResultsLimit() {
        // 造 35 条数据
        var manyChunks = new java.util.ArrayList<Chunk>();
        for (int i = 1; i <= 35; i++) {
            manyChunks.add(createChunk(i, "匹配内容 " + i, null));
        }
        when(chunkDao.grepDoc(anyLong(), anyString())).thenReturn(manyChunks);

        String result = tool.execute(10L, "匹配");

        assertTrue(result.contains("30 处匹配"), "应只显示 30 条");
        // 应该没有第 31 条
        assertFalse(result.contains("切片 31"), "不应超过 30 条限制");
    }

    private Chunk createChunk(int seq, String content, String headingPath) {
        Chunk c = new Chunk();
        c.setId((long) seq);
        c.setDocId(10L);
        c.setKbId(1L);
        c.setSeq(seq);
        c.setContent(content);
        c.setHeadingPath(headingPath);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }
}
