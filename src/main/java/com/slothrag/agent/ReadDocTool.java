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
 * 文档读取工具（`read_doc`），按文档 ID 和切片序号范围读取内容。
 * <p>
 * 配合 search_kb 使用：search_kb 定位到相关文档和切片后，
 * 如果 LLM 需要查看更多上下文，可调用此工具读取指定范围内的连续切片。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReadDocTool {

    public static final String NAME = "read_doc";

    private static final String DESCRIPTION =
            "按文档 ID 和切片序号范围读取知识库文档的完整内容。"
            + "当你通过 search_kb 定位到某个文档后，需要查看更多上下文或阅读完整内容时调用。";

    private static final Map<String, Object> PARAMETERS = Map.of(
            "type", "object",
            "properties", Map.of(
                    "doc_id", Map.of(
                            "type", "integer",
                            "description", "文档 ID，来自 search_kb 返回结果中的 doc_id"),
                    "seq_start", Map.of(
                            "type", "integer",
                            "description", "起始切片序号（从 1 开始），默认 1"),
                    "seq_end", Map.of(
                            "type", "integer",
                            "description", "结束切片序号（含），默认读取 20 个切片")),
            "required", List.of("doc_id"));

    private static final ToolDefinition DEFINITION =
            new ToolDefinition(NAME, DESCRIPTION, PARAMETERS);

    /** 一次最多读取的切片数量 */
    private static final int MAX_CHUNKS = 50;

    private final ChunkDao chunkDao;

    public ToolDefinition definition() {
        return DEFINITION;
    }

    /**
     * 执行读取
     *
     * @param docId    文档 ID
     * @param seqStart 起始序号（可空，默认 1）
     * @param seqEnd   结束序号（可空，默认读取 20 条）
     * @return 格式化后的文档内容文本
     */
    public String execute(Long docId, Integer seqStart, Integer seqEnd) {
        int start = (seqStart != null && seqStart >= 1) ? seqStart : 1;
        int end;
        if (seqEnd != null && seqEnd >= start) {
            end = seqEnd;
        } else {
            end = start + 19; // 默认读 20 条
        }
        // 限制单次最大读取量
        if (end - start + 1 > MAX_CHUNKS) {
            end = start + MAX_CHUNKS - 1;
        }

        List<Chunk> chunks = chunkDao.readDoc(docId, start, end);
        if (chunks.isEmpty()) {
            return "文档 ID " + docId + " 在序号范围 " + start + "-" + end + " 内没有找到内容。";
        }

        StringBuilder sb = new StringBuilder();
        for (Chunk c : chunks) {
            sb.append("【切片 ").append(c.getSeq()).append("】");
            if (c.getHeadingPath() != null && !c.getHeadingPath().isEmpty()) {
                sb.append(" [章节：").append(c.getHeadingPath()).append("]");
            }
            sb.append("\n").append(c.getContent()).append("\n\n");
        }
        sb.append("（共 ").append(chunks.size()).append(" 个切片，序号范围 ").append(start).append("-").append(end).append("）");

        log.info("read_doc: docId={} seq=[{}-{}] 返回 {} 个切片", docId, start, end, chunks.size());
        return sb.toString();
    }
}
