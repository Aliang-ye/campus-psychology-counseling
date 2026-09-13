package com.example.psychology.service;

import com.example.psychology.common.ApiResponse;
import com.example.psychology.security.AuthUser;
import com.example.psychology.security.ForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatService(JdbcTemplate jdbcTemplate, StringRedisTemplate stringRedisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public List<Map<String, Object>> searchUsers(String username) {
        return jdbcTemplate.queryForList(
                "SELECT id, username, role, status FROM user WHERE username LIKE ? LIMIT 20",
                "%" + username + "%");
    }

    public List<Map<String, Object>> listUsers(Long currentUserId) {
        return jdbcTemplate.queryForList(
                "SELECT id, username, role, status FROM user WHERE id <> ? ORDER BY username ASC",
                currentUserId);
    }

    public Map<String, Object> updateStatus(Long userId, String status) {
        if (!"available".equals(status) && !"busy".equals(status)) {
            return ApiResponse.fail("无效的状态值");
        }
        int updated = jdbcTemplate.update("UPDATE user SET status = ? WHERE id = ?", status, userId);
        Map<String, Object> result = ApiResponse.success();
        result.put("success", updated > 0);
        result.put("status", status);
        return result;
    }

    public Map<String, Object> sendRequest(Long fromUserId, Long toUserId) {
        if (toUserId == null) {
            return ApiResponse.fail("请选择要咨询的用户");
        }
        if (fromUserId.equals(toUserId)) {
            return ApiResponse.fail("不能向自己发起咨询");
        }

        List<Map<String, Object>> target = jdbcTemplate.queryForList(
                "SELECT status FROM user WHERE id = ?", toUserId);
        if (target.isEmpty()) {
            return ApiResponse.fail("对方用户不存在");
        }
        if ("busy".equals(target.get(0).get("status"))) {
            return ApiResponse.fail("对方当前忙碌，无法接受咨询");
        }

        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM chat_request WHERE from_user_id = ? AND to_user_id = ? AND status = 'PENDING'",
                fromUserId, toUserId);
        if (!existing.isEmpty()) {
            return ApiResponse.fail("已有待处理的咨询申请，请等待对方回复");
        }

        String insert = "INSERT INTO chat_request (from_user_id, to_user_id, status, created_at, updated_at) VALUES (?, ?, 'PENDING', NOW(), NOW())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, fromUserId);
            ps.setLong(2, toUserId);
            return ps;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            return ApiResponse.fail("发起咨询失败");
        }
        Long requestId = generatedId.longValue();
        publish("INVITE", Map.of(
                "fromUserId", fromUserId,
                "toUserId", toUserId,
                "requestId", requestId));

        Map<String, Object> res = ApiResponse.success();
        res.put("requestId", requestId);
        res.put("status", "PENDING");
        return res;
    }

    @Transactional
    public Map<String, Object> acceptRequest(Long requestId, Long acceptorId) {
        Map<String, Object> req = jdbcTemplate.queryForMap(
                "SELECT from_user_id, to_user_id, status FROM chat_request WHERE id = ?", requestId);
        Long fromUserId = ((Number) req.get("from_user_id")).longValue();
        Long toUserId = ((Number) req.get("to_user_id")).longValue();
        String status = (String) req.get("status");
        if (!"PENDING".equals(status)) {
            throw new IllegalArgumentException("请求不在待处理状态");
        }
        if (!acceptorId.equals(toUserId)) {
            throw new ForbiddenException("只有被申请方可以接受咨询");
        }

        int claimed = jdbcTemplate.update(
                "UPDATE chat_request SET status='ACCEPTED', updated_at=NOW() WHERE id = ? AND to_user_id = ? AND status='PENDING'",
                requestId, acceptorId);
        if (claimed == 0) {
            throw new IllegalStateException("请求已被处理，请刷新后重试");
        }

        String insertSession = "INSERT INTO chat_session (user_a, user_b, status, created_at, updated_at) VALUES (?, ?, 'ACTIVE', NOW(), NOW())";
        KeyHolder kh = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(insertSession, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, fromUserId);
            ps.setLong(2, toUserId);
            return ps;
        }, kh);
        Number generatedId = kh.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("无法创建咨询会话");
        }
        Long sessionId = generatedId.longValue();
        jdbcTemplate.update(
                "UPDATE chat_request SET session_id=?, updated_at=NOW() WHERE id = ?",
                sessionId, requestId);
        publish("ACCEPT", Map.of(
                "fromUserId", acceptorId,
                "sessionId", sessionId,
                "requestId", requestId));

        Map<String, Object> res = new HashMap<>();
        res.put("sessionId", sessionId);
        res.put("requestId", requestId);
        res.put("status", "ACTIVE");
        return res;
    }

    @Transactional
    public Map<String, Object> rejectRequest(Long requestId, Long rejectorId) {
        Map<String, Object> req = jdbcTemplate.queryForMap(
                "SELECT from_user_id, to_user_id FROM chat_request WHERE id = ?", requestId);
        Long toUserId = ((Number) req.get("to_user_id")).longValue();
        if (!rejectorId.equals(toUserId)) {
            throw new ForbiddenException("只有被申请方可以拒绝咨询");
        }
        int updated = jdbcTemplate.update(
                "UPDATE chat_request SET status='REJECTED', updated_at=NOW() WHERE id = ? AND to_user_id = ? AND status='PENDING'",
                requestId, rejectorId);
        if (updated == 0) {
            throw new IllegalStateException("请求已被处理，请刷新后重试");
        }
        publish("REJECT", Map.of("fromUserId", rejectorId, "requestId", requestId));
        Map<String, Object> res = new HashMap<>();
        res.put("requestId", requestId);
        res.put("status", "REJECTED");
        return res;
    }

    public List<Map<String, Object>> pendingRequests(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT id, from_user_id, to_user_id, status, created_at FROM chat_request WHERE to_user_id = ? AND status = 'PENDING' ORDER BY created_at DESC",
                userId);
    }

    public List<Map<String, Object>> activeSessions(Long userId) {
        String sql = "SELECT " +
                "cs.id, cs.user_a, cs.user_b, cs.status, cs.created_at, cs.updated_at, " +
                "ua.username AS username_a, ub.username AS username_b, " +
                "ua.role AS role_a, ub.role AS role_b " +
                "FROM chat_session cs " +
                "LEFT JOIN user ua ON cs.user_a = ua.id " +
                "LEFT JOIN user ub ON cs.user_b = ub.id " +
                "WHERE (cs.user_a = ? OR cs.user_b = ?) AND cs.status = 'ACTIVE' " +
                "ORDER BY cs.updated_at DESC";
        return jdbcTemplate.queryForList(sql, userId, userId);
    }

    public Map<String, Object> submitTeacherReview(AuthUser currentUser, Long teacherId, String content) {
        String review = content == null ? "" : content.trim();
        if (review.isEmpty()) {
            return ApiResponse.fail("评价内容不能为空");
        }
        if (review.length() > 500) {
            return ApiResponse.fail("评价内容不能超过500字");
        }
        if (teacherId == null) {
            return ApiResponse.fail("请选择要评价的教师");
        }

        List<Map<String, Object>> reviewerRows = jdbcTemplate.queryForList(
                "SELECT username, role FROM user WHERE id = ?", currentUser.getUserId());
        List<Map<String, Object>> teacherRows = jdbcTemplate.queryForList(
                "SELECT username, role FROM user WHERE id = ?", teacherId);
        if (reviewerRows.isEmpty() || teacherRows.isEmpty()) {
            return ApiResponse.fail("用户信息不存在");
        }

        String reviewerRole = String.valueOf(reviewerRows.get(0).get("role"));
        String teacherRole = String.valueOf(teacherRows.get(0).get("role"));
        boolean teacherLike = "teacher".equals(teacherRole) || "doctor".equals(teacherRole);
        if (!"student".equals(reviewerRole) || !teacherLike) {
            return ApiResponse.fail("仅学生可评价教师");
        }

        List<Map<String, Object>> consultRows = jdbcTemplate.queryForList(
                "SELECT id FROM chat_request WHERE from_user_id = ? AND to_user_id = ? ORDER BY created_at DESC LIMIT 1",
                currentUser.getUserId(), teacherId);
        if (consultRows.isEmpty()) {
            return ApiResponse.fail("仅可评价你发起过咨询的教师");
        }

        Long requestId = ((Number) consultRows.get(0).get("id")).longValue();
        jdbcTemplate.update(
                "INSERT INTO consult_teacher_review (request_id, reviewer_id, reviewer_name, teacher_id, teacher_name, content, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW())",
                requestId,
                currentUser.getUserId(),
                reviewerRows.get(0).get("username"),
                teacherId,
                teacherRows.get(0).get("username"),
                review);
        return ApiResponse.successMessage("评价提交成功");
    }

    public Map<String, Object> listReviews(AuthUser currentUser) {
        if (!currentUser.isAdmin()) {
            throw new ForbiddenException("只有管理员可以查看评价");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, request_id, reviewer_id, reviewer_name, teacher_id, teacher_name, content, created_at FROM consult_teacher_review ORDER BY created_at DESC");
        return ApiResponse.success("reviews", rows);
    }

    public List<Map<String, Object>> getMessages(AuthUser currentUser, Long sessionId) {
        assertSessionMember(currentUser, sessionId, true);
        return jdbcTemplate.queryForList(
                "SELECT id, session_id, sender_id, receiver_id, content, is_read, created_at FROM chat_message WHERE session_id = ? ORDER BY created_at ASC",
                sessionId);
    }

    public Map<String, Object> closeSession(AuthUser currentUser, Long sessionId) {
        assertSessionMember(currentUser, sessionId, false);
        jdbcTemplate.update("UPDATE chat_session SET status='CLOSED', updated_at=NOW() WHERE id = ?", sessionId);
        Map<String, Object> res = new HashMap<>();
        res.put("sessionId", sessionId);
        res.put("status", "CLOSED");
        return res;
    }

    private void assertSessionMember(AuthUser currentUser, Long sessionId, boolean adminAllowed) {
        Map<String, Object> session = jdbcTemplate.queryForMap(
                "SELECT user_a, user_b FROM chat_session WHERE id = ?", sessionId);
        Long userA = ((Number) session.get("user_a")).longValue();
        Long userB = ((Number) session.get("user_b")).longValue();
        boolean member = currentUser.getUserId().equals(userA) || currentUser.getUserId().equals(userB);
        if (!member && !(adminAllowed && currentUser.isAdmin())) {
            throw new ForbiddenException("无权访问该会话");
        }
    }

    private void publish(String type, Map<String, Object> extra) {
        try {
            Map<String, Object> payload = new HashMap<>(extra);
            payload.put("type", type);
            stringRedisTemplate.convertAndSend("user:requests", mapper.writeValueAsString(payload));
        } catch (Exception e) {
            System.out.println("[ChatService] publish failed: " + e.getMessage());
        }
    }
}
