package com.example.psychology.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.example.psychology.security.AuthContext;
import com.example.psychology.security.AuthUser;
import com.example.psychology.security.ForbiddenException;

import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/search")
    public List<Map<String, Object>> searchUsers(@RequestParam String username) {
        String sql = "SELECT id, username, role, status FROM user WHERE username LIKE ? LIMIT 20";
        return jdbcTemplate.queryForList(sql, "%" + username + "%");
    }

    @GetMapping("/users/all")
    public List<Map<String, Object>> getAllUsers() {
        String sql = "SELECT id, username, role, status FROM user ORDER BY username ASC";
        return jdbcTemplate.queryForList(sql);
    }

    @PostMapping("/user/status")
    public Map<String, Object> updateUserStatus(@RequestBody Map<String, Object> body) {
        Long userId = AuthContext.require().getUserId();
        String status = (String) body.get("status");
        
        if (!status.equals("available") && !status.equals("busy")) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "无效的状态值");
            return error;
        }
        
        String sql = "UPDATE user SET status = ? WHERE id = ?";
        int updated = jdbcTemplate.update(sql, status, userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", updated > 0);
        result.put("status", status);
        return result;
    }

    @PostMapping("/request/send")
    public Map<String, Object> sendRequest(@RequestBody Map<String, Object> body) {
        Long fromUserId = AuthContext.require().getUserId();
        Long toUserId = ((Number) body.get("toUserId")).longValue();

        // 检查对方用户状态
        String checkStatusSql = "SELECT status FROM user WHERE id = ?";
        List<Map<String, Object>> statusResult = jdbcTemplate.queryForList(checkStatusSql, toUserId);
        if (!statusResult.isEmpty()) {
            String userStatus = (String) statusResult.get(0).get("status");
            if ("busy".equals(userStatus)) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "对方当前忙碌，无法接受咨询");
                return error;
            }
        }

        String insert = "INSERT INTO chat_request (from_user_id, to_user_id, status, created_at, updated_at) VALUES (?, ?, 'PENDING', NOW(), NOW())";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, fromUserId);
            ps.setLong(2, toUserId);
            return ps;
        }, keyHolder);
        Long requestId = keyHolder.getKey().longValue();

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "INVITE");
            payload.put("fromUserId", fromUserId);
            payload.put("toUserId", toUserId);
            payload.put("requestId", requestId);
            String json = mapper.writeValueAsString(payload);
            stringRedisTemplate.convertAndSend("user:requests", json);
            System.out.println("[ChatController] sent request -> " + json);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("requestId", requestId);
        res.put("status", "PENDING");
        return res;
    }

    @PostMapping("/request/accept")
    @Transactional
    public Map<String, Object> acceptRequest(@RequestBody Map<String, Object> body) {
        Long requestId = ((Number) body.get("requestId")).longValue();
        Long acceptorId = AuthContext.require().getUserId();

        Map<String, Object> req = jdbcTemplate.queryForMap(
            "SELECT from_user_id, to_user_id, status FROM chat_request WHERE id = ?", requestId);
        Long fromUserId = ((Number) req.get("from_user_id")).longValue();
        Long toUserId = ((Number) req.get("to_user_id")).longValue();
        String status = (String) req.get("status");

        if (!"PENDING".equals(status)) {
            throw new IllegalArgumentException("Request not in PENDING state");
        }
        if (!acceptorId.equals(toUserId)) {
            throw new IllegalArgumentException("Only the request receiver can accept");
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

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "ACCEPT");
            payload.put("fromUserId", acceptorId);
            payload.put("sessionId", sessionId);
            payload.put("requestId", requestId);
            String json = mapper.writeValueAsString(payload);
            stringRedisTemplate.convertAndSend("user:requests", json);
            System.out.println("[ChatController] accepted request -> " + json);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, Object> res = new HashMap<>();
        res.put("sessionId", sessionId);
        res.put("requestId", requestId);
        res.put("status", "ACTIVE");
        return res;
    }

    @PostMapping("/request/reject")
    @Transactional
    public Map<String, Object> rejectRequest(@RequestBody Map<String, Object> body) {
        Long requestId = ((Number) body.get("requestId")).longValue();
        Long rejectorId = AuthContext.require().getUserId();

        Map<String, Object> req = jdbcTemplate.queryForMap(
            "SELECT from_user_id, to_user_id FROM chat_request WHERE id = ?", requestId);
        Long fromUserId = ((Number) req.get("from_user_id")).longValue();
        Long toUserId = ((Number) req.get("to_user_id")).longValue();
        if (!rejectorId.equals(toUserId)) {
            throw new IllegalArgumentException("Only the request receiver can reject");
        }

        int updated = jdbcTemplate.update(
            "UPDATE chat_request SET status='REJECTED', updated_at=NOW() WHERE id = ? AND to_user_id = ? AND status='PENDING'",
            requestId, rejectorId);
        if (updated == 0) {
            throw new IllegalStateException("请求已被处理，请刷新后重试");
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "REJECT");
            payload.put("fromUserId", rejectorId);
            payload.put("requestId", requestId);
            String json = mapper.writeValueAsString(payload);
            stringRedisTemplate.convertAndSend("user:requests", json);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Map<String, Object> res = new HashMap<>();
        res.put("requestId", requestId);
        res.put("status", "REJECTED");
        return res;
    }

    @GetMapping("/request/pending")
    public List<Map<String, Object>> getPendingRequests() {
        Long userId = AuthContext.require().getUserId();
        String sql = "SELECT id, from_user_id, to_user_id, status, created_at FROM chat_request WHERE to_user_id = ? AND status = 'PENDING' ORDER BY created_at DESC";
        return jdbcTemplate.queryForList(sql, userId);
    }

    @GetMapping("/sessions")
    public List<Map<String, Object>> getSessions() {
        Long userId = AuthContext.require().getUserId();
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

    @PostMapping("/review")
    public Map<String, Object> submitTeacherReview(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long reviewerId = AuthContext.require().getUserId();
            Long teacherId = ((Number) body.get("teacherId")).longValue();
            String content = body.get("content") == null ? "" : String.valueOf(body.get("content")).trim();

            if (content.isEmpty()) {
                response.put("success", false);
                response.put("message", "评价内容不能为空");
                return response;
            }
            if (content.length() > 500) {
                response.put("success", false);
                response.put("message", "评价内容不能超过500字");
                return response;
            }

            List<Map<String, Object>> reviewerRows = jdbcTemplate.queryForList(
                "SELECT username, role FROM user WHERE id = ?",
                reviewerId
            );
            List<Map<String, Object>> teacherRows = jdbcTemplate.queryForList(
                "SELECT username, role FROM user WHERE id = ?",
                teacherId
            );

            if (reviewerRows.isEmpty() || teacherRows.isEmpty()) {
                response.put("success", false);
                response.put("message", "用户信息不存在");
                return response;
            }

            String reviewerRole = String.valueOf(reviewerRows.get(0).get("role"));
            String teacherRole = String.valueOf(teacherRows.get(0).get("role"));
            String reviewerName = String.valueOf(reviewerRows.get(0).get("username"));
            String teacherName = String.valueOf(teacherRows.get(0).get("username"));

            boolean teacherLike = "teacher".equals(teacherRole) || "doctor".equals(teacherRole);
            if (!"student".equals(reviewerRole) || !teacherLike) {
                response.put("success", false);
                response.put("message", "仅学生可评价教师");
                return response;
            }

            List<Map<String, Object>> consultRows = jdbcTemplate.queryForList(
                "SELECT id FROM chat_request WHERE from_user_id = ? AND to_user_id = ? ORDER BY created_at DESC LIMIT 1",
                reviewerId, teacherId
            );

            if (consultRows.isEmpty()) {
                response.put("success", false);
                response.put("message", "仅可评价你发起过咨询的教师");
                return response;
            }

            Long requestId = ((Number) consultRows.get(0).get("id")).longValue();

            jdbcTemplate.update(
                "INSERT INTO consult_teacher_review (request_id, reviewer_id, reviewer_name, teacher_id, teacher_name, content, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW())",
                requestId, reviewerId, reviewerName, teacherId, teacherName, content
            );

            response.put("success", true);
            response.put("message", "评价提交成功");
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "评价提交失败: " + e.getMessage());
            return response;
        }
    }

    @GetMapping("/reviews")
    public Map<String, Object> getTeacherReviews() {
        Map<String, Object> response = new HashMap<>();
        if (!AuthContext.require().isAdmin()) {
            throw new ForbiddenException("只有管理员可以查看评价");
        }
        try {
            String sql = "SELECT id, request_id, reviewer_id, reviewer_name, teacher_id, teacher_name, content, created_at " +
                    "FROM consult_teacher_review ORDER BY created_at DESC";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            response.put("success", true);
            response.put("reviews", rows);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "查询评价失败: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/messages")
    public List<Map<String, Object>> getMessages(@RequestParam Long sessionId) {
        AuthUser currentUser = AuthContext.require();
        Map<String, Object> session = jdbcTemplate.queryForMap(
            "SELECT user_a, user_b FROM chat_session WHERE id = ?", sessionId);
        Long userA = ((Number) session.get("user_a")).longValue();
        Long userB = ((Number) session.get("user_b")).longValue();
        if (!currentUser.getUserId().equals(userA) && !currentUser.getUserId().equals(userB) && !currentUser.isAdmin()) {
            throw new ForbiddenException("无权查看该会话消息");
        }
        String sql = "SELECT id, session_id, sender_id, receiver_id, content, is_read, created_at FROM chat_message WHERE session_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.queryForList(sql, sessionId);
    }

    @PostMapping("/session/close")
    public Map<String, Object> closeSession(@RequestBody Map<String, Object> body) {
        Long sessionId = ((Number) body.get("sessionId")).longValue();
        Long userId = AuthContext.require().getUserId();

        Map<String, Object> session = jdbcTemplate.queryForMap(
            "SELECT user_a, user_b FROM chat_session WHERE id = ?", sessionId);
        Long userA = ((Number) session.get("user_a")).longValue();
        Long userB = ((Number) session.get("user_b")).longValue();

        if (!userId.equals(userA) && !userId.equals(userB)) {
            throw new IllegalArgumentException("User not part of session");
        }

        jdbcTemplate.update("UPDATE chat_session SET status='CLOSED', updated_at=NOW() WHERE id = ?", sessionId);

        Map<String, Object> res = new HashMap<>();
        res.put("sessionId", sessionId);
        res.put("status", "CLOSED");
        return res;
    }
}
