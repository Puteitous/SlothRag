package com.slothrag.knowledge.chunk;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.slothrag.knowledge.domain.block.Block;
import com.slothrag.knowledge.domain.block.CodeBlock;
import com.slothrag.knowledge.domain.block.HeadingBlock;
import com.slothrag.knowledge.domain.block.ParagraphBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Block-Aware 分块器：按标题/段落/代码等结构块类型智能分块
 * <p>
 * 策略：
 * 1. HeadingBlock → 作为新的章节起点，标题内容计入章节路径
 * 2. ParagraphBlock → 归入当前章节，由 Packer 合并到目标大小
 * 3. CodeBlock → 保持完整不拆分（如果超过 targetSize 则独占一块）
 */
@Slf4j
@Component
public class BlockAwareChunker implements TextChunker {

    private static final int MIN_CHUNK_SIZE = 100;

    @Override
    public List<ChunkDraft> chunk(List<Block> blocks, int targetSize, long kbId) {
        List<ChunkDraft> drafts = new ArrayList<>();
        // 当前章节路径栈（按标题层级）
        List<String> headingStack = new ArrayList<>();
        StringBuilder currentBuffer = new StringBuilder();
        int seq = 0;

        for (Block block : blocks) {
            switch (block) {
                case HeadingBlock hb -> {
                    // 遇新标题：先 flush 缓冲区（把前一个 section 的内容输出）
                    if (!currentBuffer.isEmpty()) {
                        drafts.add(new ChunkDraft(currentBuffer.toString().trim(),
                                String.join(" > ", headingStack), seq++));
                        currentBuffer.setLength(0);
                    }
                    // 更新章节路径：同层替换，子层追加
                    updateHeadingStack(headingStack, hb.level(), hb.title());
                    // 标题文本也作为内容
                    currentBuffer.append(hb.text());
                }
                case ParagraphBlock pb -> {
                    currentBuffer.append(pb.text()).append('\n');
                    // 如果缓冲区已超过 targetSize，强制截断（在当前段落后切一刀）
                    if (currentBuffer.length() >= targetSize) {
                        drafts.add(new ChunkDraft(currentBuffer.toString().trim(),
                                String.join(" > ", headingStack), seq++));
                        currentBuffer.setLength(0);
                    }
                }
                case CodeBlock cb -> {
                    // 代码块：先 flush 缓冲区，再将代码块单独成块（超过 targetSize 也保持完整）
                    if (!currentBuffer.isEmpty()) {
                        drafts.add(new ChunkDraft(currentBuffer.toString().trim(),
                                String.join(" > ", headingStack), seq++));
                        currentBuffer.setLength(0);
                    }
                    String codeText = cb.text();
                    if (codeText.length() >= MIN_CHUNK_SIZE) {
                        // 代码块够长则独占一块
                        drafts.add(new ChunkDraft(codeText.trim(),
                                String.join(" > ", headingStack), seq++));
                    } else {
                        // 小代码块追加到缓冲区
                        currentBuffer.append(codeText).append('\n');
                    }
                }
            }
        }
        // 最后 flush 剩余内容
        if (!currentBuffer.isEmpty()) {
            drafts.add(new ChunkDraft(currentBuffer.toString().trim(),
                    String.join(" > ", headingStack), seq));
        }

        log.info("BlockAwareChunker: {} blocks → {} drafts (targetSize={})", blocks.size(), drafts.size(), targetSize);
        return drafts;
    }

    /**
     * 更新章节路径栈：
     * - level=1 → 清空栈 + 追加（根标题）
     * - level>1, 且栈深度 ≥ level → 替换同层标题（pop 到 level-1 再 push）
     * - level>1, 且栈深度 < level → 追加（子标题）
     */
    static void updateHeadingStack(List<String> stack, int level, String title) {
        // 清理栈到 level-1 深度（1-based level）
        while (stack.size() >= level) {
            stack.removeLast();
        }
        stack.add(title.trim());
    }
}
