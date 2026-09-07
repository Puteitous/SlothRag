package com.ragbase.conversation;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 会话元数据 DAO（conversation 表，JdbcTemplate 风格仿 KbDao）
 */
@Repository
@RequiredArgsConstructor
public class ConversationDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<Conversation> MAPPER = (rs, i) -> {
        Conversation c = new Conversation();
        c.setId(rs.getLong("id"));
        c.setSessionId(rs.getString("session_id"));
        c.setTitle(rs.getString("title"));
        c.setUserId(rs.getObject("user_id") == null ? null : rs.getLong("user_id"));
        c.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        c.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return c;
    };

    public List<Conversation> list(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return jdbc.query("""
                SELECT id, session_id, title, user_id, created_at, updated_at
                FROM conversation
                ORDER BY updated_at DESC
                LIMIT ? OFFSET ?
                """, MAPPER, pageSize, offset);
    }

    /**
     * 首问登录元数据；会话已存在则仅滚动 updated_at（保留首次 title）。
     * 原子化避免 exists+insert 的竞态。
     */
    public void upsert(String sessionId, String title) {
        jdbc.update("""
                INSERT INTO conversation (session_id, title) VALUES (?, ?)
                ON CONFLICT (session_id) DO UPDATE SET updated_at = now()
                """, sessionId, title);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM conversation", Long.class);
        return n == null ? 0 : n;
    }

    public void deleteBySessionId(String sessionId) {
        jdbc.update("DELETE FROM conversation WHERE session_id = ?", sessionId);
    }
}