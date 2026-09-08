package com.slothrag.chat.controller;

import com.slothrag.chat.service.ChatService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 问答 API（SSE 流式）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 流式问答（支持多轮，无知识库时退化为纯 LLM 对话）
     * 事件：session（会话 id）/ delta（回答增量）/ sources（来源引用）/ error（错误）
     *
     * @param kbId 知识库 id；为 null 时走纯 LLM 对话模式（不检索知识库）
     */
    @GetMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestParam @NotBlank String question,
                           @RequestParam(required = false) Long kbId,
                           @RequestParam(required = false) String conversationId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        chatService.streamChat(question, kbId, conversationId, emitter);
        return emitter;
    }
}
