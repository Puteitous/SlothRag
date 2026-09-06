package com.ragbase.knowledge.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 入库任务
 */
@Data
public class IngestTask {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private Long id;
    private Long docId;
    private Long kbId;
    private String status;
    private Integer progress;
    private String currentStage;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
