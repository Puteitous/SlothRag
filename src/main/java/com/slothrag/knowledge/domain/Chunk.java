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
    /** 向量数组，入库时转换为 pgvector */
    private float[] vector;
    private LocalDateTime createdAt;
}
