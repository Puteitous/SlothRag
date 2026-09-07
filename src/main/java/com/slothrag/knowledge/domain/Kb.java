package com.slothrag.knowledge.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库
 */
@Data
public class Kb {

    private Long id;
    private String name;
    private String description;
    private String embeddingModel;
    /** 文档数量（列表查询时带出） */
    private Integer docCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
