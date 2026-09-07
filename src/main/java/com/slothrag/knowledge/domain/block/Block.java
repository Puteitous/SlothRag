package com.slothrag.knowledge.domain.block;

/**
 * 文档结构块：解析后文档由一组 Block 构成，每种块有对应的分块策略
 */
public sealed interface Block permits HeadingBlock, ParagraphBlock, CodeBlock {

    /** 块文本内容 */
    String text();
}
