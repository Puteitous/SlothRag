package com.slothrag.knowledge.domain.block;

/**
 * 标题块：如 ## 标题 / ### 子标题
 * level 越大层级越深（1 = H1, 2 = H2, …）
 */
public record HeadingBlock(int level, String title, String text) implements Block {
    public HeadingBlock {
        if (title == null) title = "";
        if (text == null) text = "";
    }
}
