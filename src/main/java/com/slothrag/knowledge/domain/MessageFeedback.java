package com.slothrag.knowledge.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息反馈（👍👎）
 * <p>
 * 用户对 assistant 回答的 thumbs up/down，用于后续 Bad Case 分析和知识库质量评估。
 * 一条消息只允许一条反馈，重复提交会覆盖。
 */
@Data
public class MessageFeedback {

    private Long id;
    /** 关联会话 id */
    private String sessionId;
    /** 前端消息 id（assistant 消息） */
    private String messageId;
    /** 用户提问原文（方便离线分析） */
    private String question;
    /** 回答原文 */
    private String answer;
    /** thumbs_up / thumbs_down */
    private String feedback;
    /** 用户可选评论文本 */
    private String comment;
    private LocalDateTime createdAt;
}
