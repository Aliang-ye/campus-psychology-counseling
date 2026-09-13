package com.example.psychology.controller;

import com.example.psychology.security.AuthContext;
import com.example.psychology.service.ChatService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/search")
    public List<Map<String, Object>> searchUsers(@RequestParam String username) {
        return chatService.searchUsers(username);
    }

    @GetMapping("/users/all")
    public List<Map<String, Object>> getAllUsers() {
        return chatService.listUsers(AuthContext.require().getUserId());
    }

    @PostMapping("/user/status")
    public Map<String, Object> updateUserStatus(@RequestBody Map<String, Object> body) {
        return chatService.updateStatus(AuthContext.require().getUserId(), (String) body.get("status"));
    }

    @PostMapping("/request/send")
    public Map<String, Object> sendRequest(@RequestBody Map<String, Object> body) {
        Long toUserId = body.get("toUserId") == null ? null : ((Number) body.get("toUserId")).longValue();
        return chatService.sendRequest(AuthContext.require().getUserId(), toUserId);
    }

    @PostMapping("/request/accept")
    public Map<String, Object> acceptRequest(@RequestBody Map<String, Object> body) {
        Long requestId = ((Number) body.get("requestId")).longValue();
        return chatService.acceptRequest(requestId, AuthContext.require().getUserId());
    }

    @PostMapping("/request/reject")
    public Map<String, Object> rejectRequest(@RequestBody Map<String, Object> body) {
        Long requestId = ((Number) body.get("requestId")).longValue();
        return chatService.rejectRequest(requestId, AuthContext.require().getUserId());
    }

    @GetMapping("/request/pending")
    public List<Map<String, Object>> getPendingRequests() {
        return chatService.pendingRequests(AuthContext.require().getUserId());
    }

    @GetMapping("/sessions")
    public List<Map<String, Object>> getSessions() {
        return chatService.activeSessions(AuthContext.require().getUserId());
    }

    @PostMapping("/review")
    public Map<String, Object> submitTeacherReview(@RequestBody Map<String, Object> body) {
        Long teacherId = body.get("teacherId") == null ? null : ((Number) body.get("teacherId")).longValue();
        String content = body.get("content") == null ? "" : String.valueOf(body.get("content"));
        return chatService.submitTeacherReview(AuthContext.require(), teacherId, content);
    }

    @GetMapping("/reviews")
    public Map<String, Object> getTeacherReviews() {
        return chatService.listReviews(AuthContext.require());
    }

    @GetMapping("/messages")
    public List<Map<String, Object>> getMessages(@RequestParam Long sessionId) {
        return chatService.getMessages(AuthContext.require(), sessionId);
    }

    @PostMapping("/session/close")
    public Map<String, Object> closeSession(@RequestBody Map<String, Object> body) {
        Long sessionId = ((Number) body.get("sessionId")).longValue();
        return chatService.closeSession(AuthContext.require(), sessionId);
    }
}
