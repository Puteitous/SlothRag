package com.slothrag.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slothrag.ai.llm.ChatMessage;
import com.slothrag.ai.llm.OpenAiCompatibleLlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 根据对话内容生成推荐问题。
 * <p>
 * 在 SSE 回答流结束后调用，异步非流式轻量 LLM 调用，
 * 生成 3 个用户可能继续追问的相关问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendedQuestionService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT =
            "你是一个知识库问答助手。请基于以下对话历史和我的回答，生成 3 个用户可能继续追问的相关问题。\n"
            + "要求：\n"
            + "1. 问题必须与对话主题密切相关，能引导用户深入了解。\n"
            + "2. 每个问题不超过 15 个字。\n"
            + "3. 直接输出 JSON 字符串数组，如 [\"问题1\", \"问题2\", \"问题3\"]。\n"
            + "4. 只输出 JSON，不要多余说明文字。";

    private final OpenAiCompatibleLlmClient llmClient;

    /**
     * 基于用户问题与回答生成推荐问题。
     *
     * @param question 用户问题
     * @param answer   助手的回答
     * @return 推荐问题列表（最多 3 个），解析失败或为空时返回空列表
     */
    public List<String> generate(String question, String answer) {
        List<ChatMessage> messages = List.of(
                ChatMessage.system(SYSTEM_PROMPT),
                ChatMessage.user("用户问题：" + question + "\n\n我的回答：" + answer)
        );

        try {
            String text = llmClient.chat(messages);
            if (text == null || text.isBlank()) {
                log.warn("推荐问题返回空内容");
                return Collections.emptyList();
            }

            // 清理可能的 markdown 代码块包裹 ```json ... ```
            String cleaned = text.trim();
            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf('\n');
                int end = cleaned.lastIndexOf("```");
                if (start > 0 && end > start) {
                    cleaned = cleaned.substring(start + 1, end).trim();
                }
            }

            List<String> questions = MAPPER.readValue(cleaned,
                    new TypeReference<List<String>>() {});
            if (questions == null || questions.isEmpty()) {
                return Collections.emptyList();
            }
            // 最多取 3 个
            return questions.subList(0, Math.min(questions.size(), 3));
        } catch (Exception e) {
            log.warn("推荐问题解析失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
