package com.slothrag.knowledge.chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * 分块打包器：将 BlockAwareChunker 产出的小块合并到目标窗口大小
 * <p>
 * 规则：
 * - 同一章节路径（headingPath）内的连续小块可以合并
 * - 不同章节路径的块不合并（保证不跨标题）
 * - 合并后超过 targetSize 则停止，多余的保留为下一块
 */
public class ChunkPacker {

    /**
     * 合并 ChunkDraft 列表，使每块接近 targetSize
     *
     * @param drafts     分块草稿列表
     * @param targetSize 目标分块大小（字符数）
     * @return 合并后的分块列表
     */
    public List<ChunkDraft> pack(List<ChunkDraft> drafts, int targetSize) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        List<ChunkDraft> result = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        String currentHeading = drafts.getFirst().headingPath();
        int seq = 0;

        for (ChunkDraft draft : drafts) {
            String heading = draft.headingPath();
            // 切换章节 → flush 缓冲区
            if (!heading.equals(currentHeading) && !buf.isEmpty()) {
                result.add(new ChunkDraft(buf.toString().trim(), currentHeading, seq++));
                buf.setLength(0);
                currentHeading = heading;
            }

            // 如果单条 draft 已经够大 → 直接输出
            if (draft.content().length() >= targetSize) {
                if (!buf.isEmpty()) {
                    result.add(new ChunkDraft(buf.toString().trim(), currentHeading, seq++));
                    buf.setLength(0);
                }
                result.add(new ChunkDraft(draft.content().trim(), heading, seq++));
                currentHeading = heading;
                continue;
            }

            // 追加到缓冲区
            String sep = buf.isEmpty() ? "" : "\n";
            if (buf.length() + sep.length() + draft.content().length() > targetSize && !buf.isEmpty()) {
                // 超过目标大小 → 先 flush 当前缓冲区，再追加新内容
                result.add(new ChunkDraft(buf.toString().trim(), currentHeading, seq++));
                buf.setLength(0);
            }
            buf.append(sep).append(draft.content());
        }

        // 最后 flush 缓冲区
        if (!buf.isEmpty()) {
            result.add(new ChunkDraft(buf.toString().trim(), currentHeading, seq));
        }

        return result;
    }
}
