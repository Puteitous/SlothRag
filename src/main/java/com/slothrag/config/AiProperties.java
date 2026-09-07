package com.slothrag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 相关配置：LLM 与 Embedding（OpenAI 兼容接口）
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Llm llm = new Llm();
    private Embedding embedding = new Embedding();
    private Rerank rerank = new Rerank();

    @Data
    public static class Llm {
        /** API 基地址，例如 https://api.deepseek.com */
        private String baseUrl;
        private String apiKey;
        private String model;
    }

    @Data
    public static class Embedding {
        /** API 基地址，例如 https://api.siliconflow.cn */
        private String baseUrl;
        private String apiKey;
        private String model;
        /** 向量维度，需与 schema.sql 中 vector(n) 一致，BGE-M3 为 1024 */
        private int dimension = 1024;
    }

    @Data
    public static class Rerank {
        /** API 基地址，例如 https://api.siliconflow.cn */
        private String baseUrl;
        private String apiKey;
        private String model;
    }
}
