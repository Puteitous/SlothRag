package com.ragbase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 问答边界配置
 */
@Data
@ConfigurationProperties(prefix = "kb.chat")
public class ChatProperties {

    /** 最低语义相似度，低于该值视为知识库未覆盖，拒绝回答 */
    private double minSimilarity = 0.35;

    /** 超范围时的规范话术（拒绝 + 转人工引导） */
    private String outOfScopeMessage = "很抱歉，这个问题超出了当前知识范围，建议联系人工客服（转8001）进行咨询。";
}
