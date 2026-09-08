package com.slothrag.knowledge.dao;

import com.slothrag.common.web.PageResult;
import com.slothrag.knowledge.domain.MessageFeedback;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

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

    private final RowMapper<MessageFeedback> ROW_MAPPER = (rs, i) -> {
        MessageFeedback mf = new MessageFeedback();
        mf.setId(rs.getLong("id"));
        mf.setSessionId(rs.getString("session_id"));
        mf.setMessageId(rs.getString("message_id"));
        mf.setQuestion(rs.getString("question"));
        mf.setAnswer(rs.getString("answer"));
        mf.setFeedback(rs.getString("feedback"));
        mf.setComment(rs.getString("comment"));
        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) mf.setCreatedAt(ts.toLocalDateTime());
        return mf;
    };

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
        var list = jdbc.query(sql, ROW_MAPPER, sessionId, messageId);
        return list.isEmpty() ? null : list.getFirst();
    }

    /**
     * 分页查询反馈列表（按时间倒排）
     *
     * @param feedbackType 可选过滤：thumbs_up / thumbs_down，传 null 或空字符串不过滤
     */
    public PageResult<MessageFeedback> list(int page, int pageSize, String feedbackType) {
        boolean hasFilter = feedbackType != null && !feedbackType.isBlank();
        int offset = (page - 1) * pageSize;

        String countSql = "SELECT COUNT(*) FROM message_feedback" + (hasFilter ? " WHERE feedback = ?" : "");
        long total = hasFilter
                ? jdbc.queryForObject(countSql, Long.class, feedbackType)
                : jdbc.queryForObject(countSql, Long.class);

        String querySql = """
                SELECT id, session_id, message_id, question, answer, feedback, comment, created_at
                FROM message_feedback
                """ + (hasFilter ? "WHERE feedback = ? " : "") + """
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """;

        List<MessageFeedback> list = hasFilter
                ? jdbc.query(querySql, ROW_MAPPER, feedbackType, pageSize, offset)
                : jdbc.query(querySql, ROW_MAPPER, pageSize, offset);

        return new PageResult<>(list, total, page, pageSize);
    }

    /**
     * 反馈统计
     */
    public FeedbackStats stats() {
        String sql = """
                SELECT
                    COUNT(*)                                           AS total,
                    COUNT(*) FILTER (WHERE feedback = 'thumbs_up')     AS thumbs_up,
                    COUNT(*) FILTER (WHERE feedback = 'thumbs_down')   AS thumbs_down
                FROM message_feedback
                """;
        return jdbc.queryForObject(sql, (rs, i) -> {
            FeedbackStats s = new FeedbackStats();
            s.setTotal(rs.getLong("total"));
            s.setThumbsUp(rs.getLong("thumbs_up"));
            s.setThumbsDown(rs.getLong("thumbs_down"));
            return s;
        });
    }

    /**
     * 差评最多的提问 Top N
     */
    public List<DownRankItem> topDownQuestions(int limit) {
        String sql = """
                SELECT question, COUNT(*) AS cnt
                FROM message_feedback
                WHERE feedback = 'thumbs_down' AND question IS NOT NULL AND question <> ''
                GROUP BY question
                ORDER BY cnt DESC
                LIMIT ?
                """;
        return jdbc.query(sql, (rs, i) -> {
            DownRankItem item = new DownRankItem();
            item.setQuestion(rs.getString("question"));
            item.setCount(rs.getInt("cnt"));
            return item;
        }, limit);
    }

    // ========================================================================
    // 内部 DTO
    // ========================================================================

    @Data
    public static class FeedbackStats {
        private long total;
        private long thumbsUp;
        private long thumbsDown;

        /** 好评率（百分比），无数据时返回 0 */
        public double likeRate() {
            return total == 0 ? 0 : Math.round(thumbsUp * 10000.0 / total) / 100.0;
        }
    }

    @Data
    public static class DownRankItem {
        private String question;
        private int count;
    }
}
