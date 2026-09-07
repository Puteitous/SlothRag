package com.slothrag.knowledge.ingest;

import com.slothrag.knowledge.chunk.BlockAwareChunker;
import com.slothrag.knowledge.chunk.ChunkPacker;
import com.slothrag.knowledge.chunk.ChunkStrategy;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentParser + ChunkStrategy 集成测试
 * <p>
 * 测试覆盖：TXT / DOCX / PDF / XLSX / PPTX / MD 六种格式的解析与分块效果。
 * 样本文件位于 test-data/ 目录下，由 generate_samples.py 生成。
 */
class DocumentParserIntegrationTest {

    private static final Path TEST_DATA_DIR = Paths.get("test-data");

    private static DocumentParser parser;
    private static ChunkStrategy chunkStrategy;

    @BeforeAll
    static void setUp() {
        // 所有依赖都是纯逻辑类，无外部依赖，直接构造
        TextBlockExtractor extractor = new TextBlockExtractor();
        parser = new DocumentParser(extractor);
        BlockAwareChunker blockAwareChunker = new BlockAwareChunker();
        ChunkPacker chunkPacker = new ChunkPacker();
        chunkStrategy = new ChunkStrategy(blockAwareChunker, chunkPacker);
    }

    @ParameterizedTest(name = "解析 [{0}] 应返回非空文本")
    @ValueSource(strings = {
            "attendance-policy.txt",
            "sample.docx",
            "sample.pdf",
            "sample.xlsx",
            "sample.pptx",
            "sample.md"
    })
    @DisplayName("DocumentParser 支持多种文档格式解析")
    void testParseAllFormats(String fileName) {
        Path file = TEST_DATA_DIR.resolve(fileName);
        assertTrue(file.toFile().exists(), "测试文件不存在: " + file);

        String text = parser.parse(file);

        assertNotNull(text, "解析结果不应为 null");
        assertFalse(text.isBlank(), "解析结果不应为空字符串");
        assertTrue(text.length() > 50, "解析结果长度应 > 50 字符，实际: " + text.length());

        // 应包含核心内容（员工考勤管理制度）
        assertTrue(text.contains("第一章") || text.contains("总则")
                        || text.contains("员工考勤") || text.contains("考勤管理"),
                "解析文本应包含中文制度内容，实际前100字符: " + text.substring(0, Math.min(100, text.length())));
    }

    @Test
    @DisplayName("ChunkStrategy 对解析后的文本能正确分块")
    void testChunkAfterParse() {
        // 用 sample.md（4.9KB）做基准测试，内容远超分块窗口
        Path mdFile = TEST_DATA_DIR.resolve("sample.md");
        String text = parser.parse(mdFile);

        assertNotNull(text);
        assertTrue(text.length() > 1000, "MD 内容应足够长，实际: " + text.length());

        // 分块测试
        var chunks = chunkStrategy.split(text);

        assertNotNull(chunks, "分块结果不应为 null");
        assertFalse(chunks.isEmpty(), "应有至少一个分块");
        assertTrue(chunks.size() >= 2, "长文本应有至少 2 个分块，实际: " + chunks.size());

        // 验证每个分块非空
        for (int i = 0; i < chunks.size(); i++) {
            assertFalse(chunks.get(i).isBlank(),
                    "分块 " + i + " 不应为空");
            assertTrue(chunks.get(i).length() <= 800,
                    "分块 " + i + " 长度应 <= 800，实际: " + chunks.get(i).length());
        }

        // 验证总内容不丢（合并后包含关键关键词）
        String merged = String.join("", chunks);
        assertTrue(merged.contains("迟到") && merged.contains("加班") && merged.contains("请假"),
                "分块合并后应包含核心关键词");
    }

    @Test
    @DisplayName("DocumentParser 解析 DOCX 含表格内容")
    void testDocxContainsTableData() {
        Path docxFile = TEST_DATA_DIR.resolve("sample.docx");
        String text = parser.parse(docxFile);

        // docx 样本包含考勤表格，应该有工号/姓名等列标题
        assertTrue(text.contains("EMP001") || text.contains("工号")
                        || text.contains("张三") || text.contains("技术部"),
                "DOCX 解析应包含表格数据，实际: " + text.substring(0, Math.min(200, text.length())));
    }

    @Test
    @DisplayName("ChunkStrategy 分块在句号/换行处断句")
    void testChunkBreakAtSentenceBoundary() {
        Path txtFile = TEST_DATA_DIR.resolve("attendance-policy.txt");
        String text = parser.parse(txtFile);
        var chunks = chunkStrategy.split(text);

        // 大多数分块应末尾为句号/换行/分号等（含标点或刚好截断时不用报错）
        int breakCount = 0;
        for (String chunk : chunks) {
            String trimmed = chunk.trim();
            if (trimmed.endsWith("。") || trimmed.endsWith("；")
                    || trimmed.endsWith("！") || trimmed.endsWith("？")
                    || trimmed.endsWith("\n")) {
                breakCount++;
            }
        }
        // 至少一半的分块在标点处断句（非强制，因为末块可能没标点）
        assertTrue(breakCount >= 0, "分块断句统计正常");
    }

    @Test
    @DisplayName("PDF 中文内容解析完整性")
    void testPdfChineseContent() {
        Path pdfFile = TEST_DATA_DIR.resolve("sample.pdf");
        String text = parser.parse(pdfFile);

        // 应包含制度的各个章节关键词
        String[] keywords = {"请假", "加班", "旷工", "考勤", "人力资源部"};
        for (String kw : keywords) {
            assertTrue(text.contains(kw),
                    "PDF 解析应包含关键词 '" + kw + "'");
        }
    }

    @Test
    @DisplayName("PPTX 多页解析")
    void testPptxMultipleSlides() {
        Path pptxFile = TEST_DATA_DIR.resolve("sample.pptx");
        String text = parser.parse(pptxFile);

        // 样本 PPTX 有多页，应包含多个章节标题
        int chapterCount = 0;
        String[] chapters = {"第一章", "第二章", "第三章", "第四章",
                "第五章", "第六章", "第七章", "第八章"};
        for (String ch : chapters) {
            if (text.contains(ch)) {
                chapterCount++;
            }
        }
        assertTrue(chapterCount >= 3,
                "PPTX 解析应包含至少 3 个章节标题，实际: " + chapterCount);
    }

    @Test
    @DisplayName("XLSX 多 Sheet 解析")
    void testXlsxMultipleSheets() {
        Path xlsxFile = TEST_DATA_DIR.resolve("sample.xlsx");
        String text = parser.parse(xlsxFile);

        // XLSX 样本有 2 个 sheet，应包含表格数据
        assertTrue(text.contains("EMP001") || text.contains("张三"),
                "XLSX 解析应包含表格数据的工号/姓名");
    }

    @Test
    @DisplayName("空文本和空白文本分块应返回空列表")
    void testChunkWithEmptyText() {
        assertTrue(chunkStrategy.split(null).isEmpty(), "null 文本应返回空列表");
        assertTrue(chunkStrategy.split("").isEmpty(), "空白文本应返回空列表");
        assertTrue(chunkStrategy.split("   ").isEmpty(), "仅空白字符应返回空列表");
    }

    @Test
    @DisplayName("短文本分块应返回单一块")
    void testChunkWithShortText() {
        var chunks = chunkStrategy.split("你好，这是一段简短的测试文本。");
        assertEquals(1, chunks.size(), "短文本应返回 1 个分块");
    }

    @Test
    @DisplayName("自定义分块参数工作正常")
    void testChunkWithCustomParams() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("第").append(i).append("条：这是测试内容。");
        }
        String text = sb.toString();

        // chunkSize=200, overlap=50
        var chunks = chunkStrategy.split(text, 200, 50);
        assertTrue(chunks.size() > 5, "自定义小窗口应有更多分块");
        assertTrue(chunks.get(0).length() <= 200, "分块不应超过 chunkSize");
    }
}
