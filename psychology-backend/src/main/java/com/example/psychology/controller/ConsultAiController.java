package com.example.psychology.controller;

import com.example.psychology.security.AuthContext;
import com.example.psychology.security.AuthUser;
import com.example.psychology.service.AiService;
import com.example.psychology.service.ConsultAiSessionService;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/consult/ai")
@CrossOrigin(origins = "*")
public class ConsultAiController {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private final AiService aiService;
    private final ConsultAiSessionService sessionService;
    private final JdbcTemplate jdbcTemplate;

    public ConsultAiController(AiService aiService, ConsultAiSessionService sessionService, JdbcTemplate jdbcTemplate) {
        this.aiService = aiService;
        this.sessionService = sessionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/session")
    public Map<String, Object> getOrCreateSession() {
        try {
            Long sessionId = sessionService.getOrCreateSession(AuthContext.require().getUserId());
            return Map.of("success", true, "sessionId", sessionId);
        } catch (Exception e) {
            return Map.of("success", false, "message", "获取会话失败: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    public Map<String, Object> getHistory(@RequestParam Long sessionId) {
        try {
            sessionService.assertOwnsSession(AuthContext.require(), sessionId);
            List<Map<String, Object>> messages = jdbcTemplate.queryForList(
                    "SELECT * FROM chat_message WHERE session_id = ? ORDER BY created_at ASC",
                    sessionId);
            return Map.of("success", true, "messages", messages);
        } catch (Exception e) {
            return Map.of("success", false, "message", "加载历史失败: " + e.getMessage());
        }
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody Map<String, Object> request) {
        Long sessionId = ((Number) request.get("sessionId")).longValue();
        AuthUser currentUser = AuthContext.require();
        sessionService.assertOwnsSession(currentUser, sessionId);
        String message = request.get("message") == null ? "" : String.valueOf(request.get("message"));
        if (message.isBlank()) {
            SseEmitter emitter = new SseEmitter(150 * 1000L);
            try {
                emitter.send(SseEmitter.event().data("[错误] 消息不能为空"));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }
        SseEmitter emitter = new SseEmitter(150 * 1000L);
        EXECUTOR.execute(() -> {
            try {
                aiService.streamChatAndSave(sessionId, currentUser.getUserId(), message, jdbcTemplate, chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk).id(System.nanoTime() + ""));
                    } catch (IOException e) {
                        System.out.println("[ConsultAiController] SSE 发送失败: " + e.getMessage());
                    }
                });
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data("[错误] " + e.getMessage()).id(System.nanoTime() + ""));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });
        return emitter;
    }

    @DeleteMapping("/history")
    public Map<String, Object> clearHistory(@RequestParam Long sessionId) {
        try {
            sessionService.assertOwnsSession(AuthContext.require(), sessionId);
            int affected = jdbcTemplate.update("DELETE FROM chat_message WHERE session_id = ?", sessionId);
            return Map.of("success", true, "deletedCount", affected);
        } catch (Exception e) {
            return Map.of("success", false, "message", "清空失败: " + e.getMessage());
        }
    }

    @PostMapping("/request-expert")
    public Map<String, Object> requestExpertConsult(@RequestBody Map<String, Object> request) {
        try {
            AuthUser currentUser = AuthContext.require();
            String reason = (String) request.get("reason");
            List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                    "SELECT id FROM consult_request WHERE user_id = ? AND status = 'PENDING'",
                    currentUser.getUserId());
            if (!existing.isEmpty()) {
                return Map.of("success", false, "message", "您已有一个待处理的专家咨询申请，请耐心等待");
            }
            jdbcTemplate.update(
                    "INSERT INTO consult_request (user_id, username, reason, status, created_at) VALUES (?, ?, ?, 'PENDING', NOW())",
                    currentUser.getUserId(), currentUser.getUsername(), reason);
            return Map.of("success", true, "message", "申请已提交，请等待心理专家的回复");
        } catch (Exception e) {
            return Map.of("success", false, "message", "申请失败: " + e.getMessage());
        }
    }

    @GetMapping("/request-status")
    public Map<String, Object> getRequestStatus() {
        try {
            Long userId = AuthContext.require().getUserId();
            List<Map<String, Object>> requests = jdbcTemplate.queryForList(
                    "SELECT id, status, consultant_name, created_at FROM consult_request WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
                    userId);
            if (requests.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("success", true);
                empty.put("hasRequest", false);
                empty.put("status", null);
                return empty;
            }
            Map<String, Object> req = requests.get(0);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("hasRequest", true);
            result.put("status", req.get("status"));
            result.put("consultantName", req.get("consultant_name"));
            result.put("createdAt", req.get("created_at"));
            return result;
        } catch (Exception e) {
            return Map.of("success", false, "message", "查询失败: " + e.getMessage());
        }
    }
}
