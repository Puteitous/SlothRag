package com.ragbase.knowledge.chunk;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 分块策略：递归字符分块 + 重叠
 * <p>
 * MVP 先做固定窗口 + overlap 的朴素版本，保证语义不截断过狠；
 * 后续可按 Markdown 标题/段落边界做结构化分块
 */
@Component
public class ChunkStrategy {

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_OVERLAP = 100;

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
            // 尽量在句号/换行处断句，避免生硬截断
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

    /**
     * 在 [from, to] 区间内查找最后一个合适断点（句号/换行），找不到返回 -1
     */
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
