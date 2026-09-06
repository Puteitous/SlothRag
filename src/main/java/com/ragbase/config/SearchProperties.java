package com.ragbase.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 检索配置
 */
@Data
@ConfigurationProperties(prefix = "kb.search")
public class SearchProperties {

    /** 是否启用 Rerank 重排（关闭时回退到向量相似度判据） */
    private boolean rerankEnabled = true;

    /** Rerank 候选数（RRF 融合后的输入规模） */
    private int rerankCandidates = 8;

    /** 最终进入上下文的条数 */
    private int contextTopK = 6;

    /** 最低 Rerank 分，整批最高分低于该值视为知识库未覆盖，整批拒绝（证据闸门） */
    private double minRerankScore = 0.3;
}
