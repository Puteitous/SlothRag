package com.ragbase.admin.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 后台用户 DAO
 */
@Repository
@RequiredArgsConstructor
public class UserDao {

    private final JdbcTemplate jdbc;

    private static final RowMapper<User> MAPPER = (rs, i) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setNickname(rs.getString("nickname"));
        u.setRole(rs.getString("role"));
        u.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        u.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
        return u;
    };

    public User findByUsername(String username) {
        return jdbc.query("SELECT * FROM \"user\" WHERE username = ?", MAPPER, username)
                .stream().findFirst().orElse(null);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM \"user\"", Long.class);
    }

    public void insert(String username, String passwordHash, String nickname, String role) {
        jdbc.update(
                "INSERT INTO \"user\" (username, password, nickname, role) VALUES (?, ?, ?, ?)",
                username, passwordHash, nickname, role);
    }
}