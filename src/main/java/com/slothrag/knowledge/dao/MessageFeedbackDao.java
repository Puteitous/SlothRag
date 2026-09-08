package com.slothrag.knowledge.dao;

import com.slothrag.knowledge.domain.MessageFeedback;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 消息反馈 DAO
 * <p>
 * upsert 语义：同一 sessionId + messageId 重复提交时覆盖 feedback/comment
 * （保留最新一次用户选择）
 */
@Repository
@RequiredArgsConstructor
public class MessageFeedbackDao {

    private final JdbcTemplate jdbc;

    /**
     * 提交或覆盖反馈
     */
    public void upsert(MessageFeedback feedback) {
        String sql = """
                INSERT INTO message_feedback (session_id, message_id, question, answer, feedback, comment)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id, message_id)
                DO UPDATE SET feedback = EXCLUDED.feedback,
                              comment  = EXCLUDED.comment,
                              created_at = now()
                """;
        jdbc.update(sql,
                feedback.getSessionId(),
                feedback.getMessageId(),
                feedback.getQuestion(),
                feedback.getAnswer(),
                feedback.getFeedback(),
                feedback.getComment());
    }

    /**
     * 查询某条消息已有的反馈（用于前端回显）
     */
    public MessageFeedback findBySessionAndMessage(String sessionId, String messageId) {
        String sql = """
                SELECT id, session_id, message_id, question, answer, feedback, comment, created_at
                FROM message_feedback
                WHERE session_id = ? AND message_id = ?
                """;
        var list = jdbc.query(sql, (rs, i) -> {
            MessageFeedback mf = new MessageFeedback();
            mf.setId(rs.getLong("id"));
            mf.setSessionId(rs.getString("session_id"));
            mf.setMessageId(rs.getString("message_id"));
            mf.setQuestion(rs.getString("question"));
            mf.setAnswer(rs.getString("answer"));
            mf.setFeedback(rs.getString("feedback"));
            mf.setComment(rs.getString("comment"));
            mf.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            return mf;
        }, sessionId, messageId);
        return list.isEmpty() ? null : list.getFirst();
    }
}
