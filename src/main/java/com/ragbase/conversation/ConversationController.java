package com.ragbase.conversation;

import com.ragbase.ai.llm.ChatMessage;
import com.ragbase.common.web.PageResult;
import com.ragbase.common.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话（历史会话列表）接口
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 会话列表（最近活跃在前）
     */
    @GetMapping
    public Result<PageResult<Conversation>> list(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(conversationService.list(page, pageSize));
    }

    /**
     * 删除会话（元数据记录 + JSONL 文件）
     */
    @DeleteMapping("/{sessionId}")
    public Result<Void> delete(@PathVariable String sessionId) {
        conversationService.delete(sessionId);
        return Result.success();
    }

    /**
     * 读取会话全部历史消息（按写入顺序）
     */
    @GetMapping("/{sessionId}/messages")
    public Result<List<ChatMessage>> messages(@PathVariable String sessionId) {
        return Result.success(conversationService.messages(sessionId));
    }
}