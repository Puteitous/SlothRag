package com.slothrag.knowledge.chunk;

/**
 * 分块草稿：Chunker 输出的中间结果，待 ChunkPacker 合并后写入库
 *
 * @param content     块文本内容
 * @param headingPath 章节路径（如 "第二章 > 2.1 > 架构"），用于追溯来源
 * @param seq         原始序号（同文档内唯一）
 */
public record ChunkDraft(String content, String headingPath, int seq) {
    public ChunkDraft {
        if (content == null) content = "";
        if (headingPath == null) headingPath = "";
    }
}
