package com.example.psychology.websocket;

import com.example.psychology.dto.ChatMessageDto;
import com.example.psychology.security.AuthUser;
import com.example.psychology.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final Map<Long, java.util.Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionIdToUserId = new ConcurrentHashMap<>();

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = extractQueryParam(session.getUri(), "token");
        if (token == null || token.isBlank()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("missing token"));
            return;
        }
        try {
            AuthUser user = jwtService.parse(token);
            Long userId = user.getUserId();
            sessionsByUser.computeIfAbsent(userId, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(session);
            sessionIdToUserId.put(session.getId(), userId);
            System.out.println("[ChatWebSocketHandler] connection established for userId=" + userId + " sessionId=" + session.getId());
        } catch (Exception e) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("invalid token"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        ChatMessageDto dto = mapper.readValue(payload, ChatMessageDto.class);

        Long derivedSender = sessionIdToUserId.get(session.getId());
        if (derivedSender == null) {
            System.out.println("[ChatWebSocketHandler] Unknown sender for sessionId=" + session.getId());
            return;
        }
        dto.setSenderId(derivedSender);

        Long sessionId = dto.getSessionId();
        Long senderId = dto.getSenderId();

        try {
            Map<String, Object> sessionData = jdbcTemplate.queryForMap(
                "SELECT user_a, user_b, status FROM chat_session WHERE id = ?", sessionId);
            Long userA = ((Number) sessionData.get("user_a")).longValue();
            Long userB = ((Number) sessionData.get("user_b")).longValue();
            String sessionStatus = String.valueOf(sessionData.get("status"));
            if (!"ACTIVE".equalsIgnoreCase(sessionStatus)) {
                System.out.println("[ChatWebSocketHandler] session is not active: " + sessionId);
                return;
            }
            if (!senderId.equals(userA) && !senderId.equals(userB)) {
                System.out.println("[ChatWebSocketHandler] sender is not a session member: " + senderId);
                return;
            }
            if (dto.getContent() == null || dto.getContent().isBlank()) {
                return;
            }

            Long receiverId = senderId.equals(userA) ? userB : userA;
            dto.setReceiverId(receiverId);
        } catch (Exception e) {
            System.out.println("[ChatWebSocketHandler] Failed to load session " + sessionId);
            e.printStackTrace();
            throw e;
        }

        try {
            String sql = "INSERT INTO chat_message (session_id, sender_id, receiver_id, content, is_read, created_at) VALUES (?, ?, ?, ?, 0, NOW())";
            jdbcTemplate.update(sql, sessionId, senderId, dto.getReceiverId(), dto.getContent());
        } catch (Exception e) {
            System.out.println("[ChatWebSocketHandler] Failed to persist message");
            e.printStackTrace();
        }

        String channel = "chat:session:" + sessionId;
        java.util.Map<String, Object> wrapper = new java.util.HashMap<>();
        wrapper.put("sourceWsId", session.getId());
        wrapper.put("message", dto);
        String toPublish = mapper.writeValueAsString(wrapper);
        stringRedisTemplate.convertAndSend(channel, toPublish);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessionsByUser.forEach((userId, set) -> set.removeIf(s -> s.getId().equals(session.getId())));
        sessionIdToUserId.remove(session.getId());
        sessionsByUser.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public void sendToUser(Long userId, String message) {
        sendToUser(userId, message, null);
    }

    public void sendToUser(Long userId, String message, String excludeWsId) {
        java.util.Set<WebSocketSession> set = sessionsByUser.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        set.forEach(ws -> {
            try {
                if (excludeWsId != null && excludeWsId.equals(ws.getId())) {
                    return;
                }
                if (ws.isOpen()) {
                    ws.sendMessage(new TextMessage(message));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public Map<String, Long> getSessionIdToUserIdSnapshot() {
        return new java.util.HashMap<>(sessionIdToUserId);
    }

    public Map<Long, List<String>> getSessionsByUserSnapshot() {
        Map<Long, List<String>> out = new java.util.HashMap<>();
        sessionsByUser.forEach((uid, set) -> out.put(uid, set.stream().map(WebSocketSession::getId).collect(Collectors.toList())));
        return out;
    }

    public Map<String, Object> loadSessionUsers(Long sessionId) {
        try {
            Map<String, Object> sessionData = jdbcTemplate.queryForMap(
                "SELECT user_a, user_b FROM chat_session WHERE id = ?", sessionId);
            Long userA = ((Number) sessionData.get("user_a")).longValue();
            Long userB = ((Number) sessionData.get("user_b")).longValue();
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("userA", userA);
            result.put("userB", userB);
            return result;
        } catch (Exception e) {
            System.out.println("[ChatWebSocketHandler] loadSessionUsers failed for sessionId=" + sessionId);
            return null;
        }
    }

    private String extractQueryParam(URI uri, String name) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String pair : uri.getQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && name.equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
