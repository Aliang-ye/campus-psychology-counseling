package com.example.psychology.service;

import com.example.psychology.security.AuthUser;
import com.example.psychology.security.ForbiddenException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.Statement;
import java.util.List;
import java.util.Map;

@Service
public class ConsultAiSessionService {
    private static final long AI_USER_ID = 0L;
    private final JdbcTemplate jdbcTemplate;

    public ConsultAiSessionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long getOrCreateSession(Long userId) {
        Long userA = Math.min(userId, AI_USER_ID);
        Long userB = Math.max(userId, AI_USER_ID);
        List<Map<String, Object>> sessions = jdbcTemplate.queryForList(
                "SELECT id FROM chat_session WHERE user_a = ? AND user_b = ?",
                userA, userB);
        if (!sessions.isEmpty()) {
            return ((Number) sessions.get(0).get("id")).longValue();
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO chat_session (user_a, user_b, created_at) VALUES (?, ?, NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userA);
            ps.setLong(2, userB);
            return ps;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("无法创建 AI 会话");
        }
        return generatedId.longValue();
    }

    public void assertOwnsSession(AuthUser currentUser, Long sessionId) {
        List<Map<String, Object>> sessions = jdbcTemplate.queryForList(
                "SELECT user_a, user_b FROM chat_session WHERE id = ?", sessionId);
        if (sessions.isEmpty()) {
            throw new ForbiddenException("会话不存在");
        }
        Long userA = ((Number) sessions.get(0).get("user_a")).longValue();
        Long userB = ((Number) sessions.get(0).get("user_b")).longValue();
        if (!currentUser.getUserId().equals(userA) && !currentUser.getUserId().equals(userB) && !currentUser.isAdmin()) {
            throw new ForbiddenException("无权访问该 AI 会话");
        }
    }
}
