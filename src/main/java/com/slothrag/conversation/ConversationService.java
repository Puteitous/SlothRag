package com.slothrag.conversation;

import com.slothrag.ai.llm.ChatMessage;
import com.slothrag.common.web.BizException;
import com.slothrag.common.web.PageResult;
import com.slothrag.session.SessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 会话元数据服务：登录/列表/删除
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    /** title 截断长度 */
    private static final int TITLE_MAX = 30;
    private static final String DEFAULT_TITLE = "新会话";

    private final ConversationDao conversationDao;
    private final SessionStore sessionStore;

    /**
     * 聊天侧回调（每轮问答都调用）：首问登录元数据，续接仅滚动时间
     */
    public void upsert(String sessionId, String firstQuestion) {
        if (!StringUtils.hasText(sessionId)) return;
        String title = StringUtils.hasText(firstQuestion)
                ? truncate(firstQuestion.trim(), TITLE_MAX)
                : DEFAULT_TITLE;
        conversationDao.upsert(sessionId, title);
    }

    public PageResult<Conversation> list(int page, int pageSize) {
        int p = Math.max(page, 1);
        int size = Math.min(Math.max(pageSize, 1), 100);
        List<Conversation> list = conversationDao.list(p, size);
        return new PageResult<>(list, conversationDao.count(), p, size);
    }

    /**
     * 读取会话全部历史消息（按写入顺序）
     */
    public List<ChatMessage> messages(String sessionId) {
        return sessionStore.loadMessages(sessionId);
    }

    public void delete(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BizException("400", "会话 id 不能为空");
        }
        conversationDao.deleteBySessionId(sessionId);
        sessionStore.deleteSessionFile(sessionId);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}