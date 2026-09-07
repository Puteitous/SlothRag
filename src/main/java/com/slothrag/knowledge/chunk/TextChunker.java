package com.slothrag.knowledge.chunk;

import java.util.List;

import com.slothrag.knowledge.domain.block.Block;

/**
 * 文本分块器：将一组结构块（Block）切分为 ChunkDraft 列表
 */
public interface TextChunker {

    /**
     * 对 blocks 进行分块
     *
     * @param blocks       解析后的结构块列表
     * @param targetSize   目标分块大小（字符数，Packer 合并时参考）
     * @param kbId         知识库 ID（用于日志上下文）
     * @return 分块草稿列表（可能小于 targetSize，后续由 Packer 合并）
     */
    List<ChunkDraft> chunk(List<Block> blocks, int targetSize, long kbId);
}
