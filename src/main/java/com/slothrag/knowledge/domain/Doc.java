package com.slothrag.knowledge.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档
 */
@Data
public class Doc {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PARSING = "PARSING";
    public static final String STATUS_CHUNKING = "CHUNKING";
    public static final String STATUS_INDEXED = "INDEXED";
    public static final String STATUS_FAILED = "FAILED";

    private Long id;
    private Long kbId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String status;
    private Integer chunkCount;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
