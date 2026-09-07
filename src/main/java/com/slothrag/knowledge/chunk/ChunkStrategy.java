package com.slothrag.knowledge.chunk;

import org.springframework.stereotype.Component;

import com.slothrag.knowledge.domain.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * 分块策略：Block-Aware 结构化分块（按标题/段落/代码边界智能切分）
 * <p>
 * 先按结构块（Block）由 BlockAwareChunker 切为草稿，
 * 再由 ChunkPacker 合并到目标窗口大小，保证不跨标题截断。
 * <p>
 * 保留旧的递归字符分块方法作为 fallback。
 */
@Component
public class ChunkStrategy {

    public static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_OVERLAP = 100;

    private final BlockAwareChunker blockAwareChunker;
    private final ChunkPacker chunkPacker;

    public ChunkStrategy(BlockAwareChunker blockAwareChunker, ChunkPacker chunkPacker) {
        this.blockAwareChunker = blockAwareChunker;
        this.chunkPacker = chunkPacker;
    }

    /**
     * Block-Aware 分块（主流程）：从结构块列表生成 ChunkDraft
     */
    public List<ChunkDraft> splitToDrafts(List<Block> blocks, long kbId) {
        List<ChunkDraft> drafts = blockAwareChunker.chunk(blocks, DEFAULT_CHUNK_SIZE, kbId);
        return chunkPacker.pack(drafts, DEFAULT_CHUNK_SIZE);
    }

    // ========== 以下为旧版递归字符分块（fallback 场景保留） ==========

    /**
     * 旧版：从纯文本递归字符分块（向后兼容）
     */
    public List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int length = text.length();
        int start = 0;
        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            if (end < length) {
                int breakPoint = findBreakPoint(text, start + chunkSize / 2, end);
                if (breakPoint > start) {
                    end = breakPoint;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= length) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }

    private int findBreakPoint(String text, int from, int to) {
        for (int i = to; i >= from; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '；') {
                return i + 1;
            }
        }
        return -1;
    }
}
