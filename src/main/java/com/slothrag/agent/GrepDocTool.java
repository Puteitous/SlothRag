package com.slothrag.agent;

import com.slothrag.ai.llm.ToolDefinition;
import com.slothrag.knowledge.dao.ChunkDao;
import com.slothrag.knowledge.domain.Chunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 文档内搜索工具（`grep_doc`），在指定知识库文档中搜索关键词。
 * <p>
 * 配合 search_kb 使用：当 search_kb 定位到某个文档后，
 * 如需确认文档中是否包含某个具体表述或查找特定细节，可调用此工具精确搜索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrepDocTool {

    public static final String NAME = "grep_doc";

    private static final String DESCRIPTION =
            "在指定的知识库文档中搜索关键词，返回包含该关键词的所有切片。"
            + "当你通过 search_kb 定位到某个文档后，需要确认其中的具体细节或验证某个表述时调用。";

    private static final Map<String, Object> PARAMETERS = Map.of(
            "type", "object",
            "properties", Map.of(
                    "doc_id", Map.of(
                            "type", "integer",
                            "description", "文档 ID，来自 search_kb 返回结果中的 doc_id"),
                    "keyword", Map.of(
                            "type", "string",
                            "description", "要搜索的关键词或短语")),
            "required", List.of("doc_id", "keyword"));

    /** 单次搜索返回的最大切片数 */
    private static final int MAX_RESULTS = 30;

    private static final ToolDefinition DEFINITION =
            new ToolDefinition(NAME, DESCRIPTION, PARAMETERS);

    private final ChunkDao chunkDao;

    public ToolDefinition definition() {
        return DEFINITION;
    }

    /**
     * 执行文档内搜索
     *
     * @param docId   文档 ID
     * @param keyword 搜索关键词
     * @return 格式化后的匹配内容文本
     */
    public String execute(Long docId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return "搜索关键词不能为空。";
        }

        List<Chunk> chunks = chunkDao.grepDoc(docId, keyword.trim());
        if (chunks.isEmpty()) {
            return "文档 ID " + docId + " 中没有找到包含「" + keyword.trim() + "」的内容。";
        }

        // 限制返回数量
        if (chunks.size() > MAX_RESULTS) {
            chunks = chunks.subList(0, MAX_RESULTS);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("在文档 ID ").append(docId).append(" 中搜索「").append(keyword.trim()).append("」共找到 ")
                .append(chunks.size()).append(" 处匹配：\n\n");

        for (Chunk c : chunks) {
            sb.append("【切片 ").append(c.getSeq()).append("】");
            if (c.getHeadingPath() != null && !c.getHeadingPath().isEmpty()) {
                sb.append(" [章节：").append(c.getHeadingPath()).append("]");
            }
            sb.append("\n").append(c.getContent()).append("\n\n");
        }

        log.info("grep_doc: docId={} keyword='{}' 命中 {} 个切片", docId, keyword, chunks.size());
        return sb.toString();
    }
}
