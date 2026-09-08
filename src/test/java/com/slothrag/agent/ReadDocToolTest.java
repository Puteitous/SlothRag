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
 * ReadDocTool 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ReadDocToolTest {

    @Mock
    private ChunkDao chunkDao;

    private ReadDocTool tool;

    @BeforeEach
    void setUp() {
        tool = new ReadDocTool(chunkDao);
    }

    // ── 正常读取测试 ───────────────────────────────

    @Test
    @DisplayName("指定序号范围应返回格式化内容")
    void testReadDocNormal() {
        when(chunkDao.readDoc(eq(10L), eq(1), eq(5))).thenReturn(List.of(
                createChunk(1, "第一章内容", "第一章"),
                createChunk(2, "第二章内容", "第二章")));

        String result = tool.execute(10L, 1, 5);

        assertTrue(result.contains("切片 1"));
        assertTrue(result.contains("第一章内容"));
        assertTrue(result.contains("第一章"));
        assertTrue(result.contains("切片 2"));
        assertTrue(result.contains("第二章内容"));
        assertTrue(result.contains("第二章"));
        assertTrue(result.contains("共 2 个切片"));

        verify(chunkDao, times(1)).readDoc(eq(10L), eq(1), eq(5));
    }

    // ── 默认参数测试 ───────────────────────────────

    @Test
    @DisplayName("seqStart/seqEnd 为 null 应使用默认值（1 和 20）")
    void testDefaultParameters() {
        when(chunkDao.readDoc(eq(10L), eq(1), eq(20))).thenReturn(List.of(
                createChunk(1, "内容")));

        // 两个参数都传 null
        tool.execute(10L, null, null);
        verify(chunkDao, times(1)).readDoc(eq(10L), eq(1), eq(20));
    }

    @Test
    @DisplayName("只传 seqStart 不传 seqEnd 应默认读 20 条")
    void testSeqStartOnly() {
        when(chunkDao.readDoc(eq(10L), eq(3), eq(22))).thenReturn(List.of(
                createChunk(3, "内容")));

        tool.execute(10L, 3, null);
        verify(chunkDao, times(1)).readDoc(eq(10L), eq(3), eq(22));
    }

    // ── 上限保护测试 ───────────────────────────────

    @Test
    @DisplayName("seqEnd - seqStart 超过 MAX_CHUNKS(50) 应截断")
    void testMaxChunksLimit() {
        // 请求 1-100，应截断为 1-50
        tool.execute(10L, 1, 100);
        verify(chunkDao, times(1)).readDoc(eq(10L), eq(1), eq(50));
    }

    // ── 空结果测试 ─────────────────────────────────

    @Test
    @DisplayName("没有匹配的切片应返回提示信息")
    void testEmptyResult() {
        when(chunkDao.readDoc(anyLong(), anyInt(), anyInt())).thenReturn(List.of());

        String result = tool.execute(10L, 1, 5);

        assertTrue(result.contains("没有找到内容"));
        assertTrue(result.contains("10"));
    }

    // ── 无 headingPath 测试 ────────────────────────

    @Test
    @DisplayName("headingPath 为 null 的切片不应显示章节信息")
    void testNullHeadingPath() {
        Chunk c = new Chunk();
        c.setId(1L);
        c.setDocId(10L);
        c.setSeq(1);
        c.setContent("无章节内容");
        c.setHeadingPath(null);
        c.setCreatedAt(LocalDateTime.now());
        c.setKbId(1L);

        when(chunkDao.readDoc(anyLong(), anyInt(), anyInt())).thenReturn(List.of(c));

        String result = tool.execute(10L, 1, 5);

        assertTrue(result.contains("切片 1"));
        assertTrue(result.contains("无章节内容"));
        assertFalse(result.contains("章节：")); // 没有 headingPath 时不显示章节
    }

    private Chunk createChunk(int seq, String content) {
        return createChunk(seq, content, null);
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
