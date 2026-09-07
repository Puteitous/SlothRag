package com.slothrag.knowledge.domain.block;

/**
 * 代码块
 */
public record CodeBlock(String language, String text) implements Block {
    public CodeBlock { if (language == null) language = ""; }
}
