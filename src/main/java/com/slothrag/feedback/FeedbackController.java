package com.slothrag.feedback;

import com.slothrag.common.web.Result;
import com.slothrag.knowledge.dao.MessageFeedbackDao;
import com.slothrag.knowledge.domain.MessageFeedback;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息反馈接口（👍👎）
 */
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final MessageFeedbackDao messageFeedbackDao;

    /**
     * 提交反馈（重复提交同一 messageId 会覆盖）
     */
    @PostMapping
    public Result<Void> submit(@Valid @RequestBody FeedbackRequest req) {
        MessageFeedback mf = new MessageFeedback();
        mf.setSessionId(req.getSessionId());
        mf.setMessageId(req.getMessageId());
        mf.setQuestion(req.getQuestion());
        mf.setAnswer(req.getAnswer());
        mf.setFeedback(req.getFeedback());
        mf.setComment(req.getComment());
        messageFeedbackDao.upsert(mf);
        return Result.success();
    }

    /**
     * 查询某条消息已有的反馈（前端回显）
     */
    @GetMapping
    public Result<MessageFeedback> get(@RequestParam @NotBlank String sessionId,
                                        @RequestParam @NotBlank String messageId) {
        return Result.success(messageFeedbackDao.findBySessionAndMessage(sessionId, messageId));
    }

    @Data
    public static class FeedbackRequest {
        @NotBlank
        private String sessionId;
        @NotBlank
        private String messageId;
        private String question;
        private String answer;
        @NotBlank
        private String feedback; // thumbs_up / thumbs_down
        private String comment;
    }
}
