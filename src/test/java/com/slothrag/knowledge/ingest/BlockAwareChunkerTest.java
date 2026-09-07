package com.slothrag.knowledge.ingest;

import com.slothrag.knowledge.chunk.BlockAwareChunker;
import com.slothrag.knowledge.chunk.ChunkDraft;
import com.slothrag.knowledge.chunk.ChunkPacker;
import com.slothrag.knowledge.domain.block.Block;
import com.slothrag.knowledge.domain.block.CodeBlock;
import com.slothrag.knowledge.domain.block.HeadingBlock;
import com.slothrag.knowledge.domain.block.ParagraphBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Block-Aware 分块器 & 提取器 & Packer 集成测试
 */
class BlockAwareChunkerTest {

    private final TextBlockExtractor extractor = new TextBlockExtractor();
    private final BlockAwareChunker chunker = new BlockAwareChunker();
    private final ChunkPacker packer = new ChunkPacker();

    @Test
    void testMarkdownStructure() {
        String md = """
                # 第一章 总论
                
                这是第一章的简介内容，介绍系统背景和目的。
                
                ## 1.1 系统架构
                
                系统采用微服务架构，包含以下模块：
                - 网关层
                - 业务层
                - 数据层
                
                ## 1.2 技术选型
                
                后端使用 Spring Boot，前端使用 React。
                
                ```java
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("Hello World");
                    }
                }
                ```
                
                ### 1.2.1 数据库
                
                使用 PostgreSQL + pgvector。
                """;

        List<Block> blocks = extractor.extractBlocks(md);
        System.out.println("=== Blocks ===");
        blocks.forEach(b -> System.out.println("  " + b.getClass().getSimpleName() + ": " + truncate(b.text(), 40)));

        // 验证 Block 数量和类型
        assertTrue(blocks.size() >= 6, "应提取出至少 6 个 Block");
        assertEquals(HeadingBlock.class, blocks.get(0).getClass(), "第一个应是 HeadingBlock");
        assertEquals(ParagraphBlock.class, blocks.get(1).getClass(), "第二个应是 ParagraphBlock");

        // Chunk + Pack
        List<ChunkDraft> drafts = chunker.chunk(blocks, 800, 1L);
        List<ChunkDraft> packed = packer.pack(drafts, 800);

        System.out.println("\n=== Chunk Drafts ===");
        drafts.forEach(d -> System.out.println("  [seq=" + d.seq() + "] heading=" + d.headingPath() + " | " + truncate(d.content(), 50)));

        System.out.println("\n=== Packed Chunks ===");
        packed.forEach(d -> System.out.println("  heading=" + d.headingPath() + " | " + truncate(d.content(), 50)));

        // 验证
        assertFalse(packed.isEmpty(), "打包后不应为空");
        // 验证章节路径
        assertTrue(packed.stream().anyMatch(d -> d.headingPath().contains("第一章")),
                "应有包含 '第一章' 章节路径的 chunk");
        assertTrue(packed.stream().anyMatch(d -> d.headingPath().contains("系统架构")),
                "应有包含 '系统架构' 章节路径的 chunk");

        // 验证代码块独立成块
        boolean hasCodeChunk = packed.stream().anyMatch(d -> d.content().contains("Hello World"));
        assertTrue(hasCodeChunk, "代码块应独立成块");

        System.out.println("\n✅ 所有断言通过！");
    }

    @Test
    void testPlainText() {
        // 纯文本（无标题）→ 回退到段落分块
        String text = "第一段内容。\n\n第二段内容。\n\n第三段内容。\n\n第四段内容。\n\n第五段内容。";
        List<Block> blocks = extractor.extractBlocks(text);
        assertEquals(5, blocks.size(), "应提取出 5 个 ParagraphBlock");
        blocks.forEach(b -> assertEquals(ParagraphBlock.class, b.getClass()));

        List<ChunkDraft> drafts = chunker.chunk(blocks, 800, 1L);
        List<ChunkDraft> packed = packer.pack(drafts, 800);
        assertFalse(packed.isEmpty());
    }

    @Test
    void testHeadingPath() {
        // 验证章节路径栈的正确性
        List<Block> blocks = List.of(
                new HeadingBlock(1, "H1", "H1\n"),
                new ParagraphBlock("p1"),
                new HeadingBlock(2, "H2-1", "H2-1\n"),
                new ParagraphBlock("p2"),
                new HeadingBlock(2, "H2-2", "H2-2\n"),
                new ParagraphBlock("p3"),
                new HeadingBlock(1, "H1-2", "H1-2\n"),
                new ParagraphBlock("p4")
        );

        List<ChunkDraft> drafts = chunker.chunk(blocks, 800, 1L);
        System.out.println("\n=== Heading Path 测试 ===");
        drafts.forEach(d -> System.out.println("  heading=" + d.headingPath() + " | " + truncate(d.content(), 30)));

        // p1 的路径应该是 "H1"（与标题合并）
        assertEquals("H1", drafts.get(0).headingPath());
        // p2 的路径应该是 "H1 > H2-1"（标题 + 段落合并）
        assertEquals("H1 > H2-1", drafts.get(1).headingPath());
        // p3 的路径应该是 "H1 > H2-2"
        assertEquals("H1 > H2-2", drafts.get(2).headingPath());
        // p4 的路径应该是 "H1-2"（标题 + 段落合并）
        assertEquals("H1-2", drafts.get(3).headingPath());
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
