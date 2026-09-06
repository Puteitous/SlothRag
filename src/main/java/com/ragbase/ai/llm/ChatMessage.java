package com.ragbase.ai.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 对话消息与请求体（OpenAI 兼容）
 */
public record ChatMessage(String role, String content) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            Boolean stream,
            Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens) {
    }
}
