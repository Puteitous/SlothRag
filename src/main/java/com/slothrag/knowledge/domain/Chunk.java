package com.slothrag.knowledge.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分块（含向量）
 */
@Data
public class Chunk {

    private Long id;
    private Long docId;
    private Long kbId;
    private Integer seq;
    private String content;
    /** 章节路径（如"第二章 > 2.1 > 架构"），用于来源追溯 */
    private String headingPath;
    /** 向量数组，入库时转换为 pgvector */
    private float[] vector;
    private LocalDateTime createdAt;
}
