package com.ragbase.conversation;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话元数据（对应 conversation 表）
 */
@Data
public class Conversation {

    private Long id;
    private String sessionId;
    private String title;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}