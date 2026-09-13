package com.example.psychology.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class RedisMessageSubscriber implements MessageListener {

    @Autowired(required = false)
    private ChatWebSocketHandler chatWebSocketHandler;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @Autowired(required = false)
    public void register(RedisMessageListenerContainer container) {
        // Wrap in try-catch to prevent startup blocking
        try {
            if (container == null) {
                System.out.println("[RedisMessageSubscriber] RedisMessageListenerContainer is null");
                return;
            }
            container.addMessageListener(this, new PatternTopic("chat:session:*"));
            container.addMessageListener(this, new PatternTopic("user:requests"));
            System.out.println("[RedisMessageSubscriber] Successfully registered Redis message listener");
        } catch (Exception e) {
            System.out.println("[RedisMessageSubscriber] Failed to register Redis listener, but continuing startup: " + e.getMessage());
            // Do NOT rethrow - allow app to start even if Redis subscription fails
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (chatWebSocketHandler == null) return;
        try {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());
            
            // Submit processing to background thread to avoid blocking Redis listener thread
            executor.submit(() -> {
                try {
                    if (channel.startsWith("chat:session:")) {
                        String sessionIdStr = channel.replace("chat:session:", "");
                        Long sessionId = Long.parseLong(sessionIdStr);
                        Map<String, Object> sessionData = chatWebSocketHandler.loadSessionUsers(sessionId);
                        if (sessionData != null) {
                            Long userA = (Long) sessionData.get("userA");
                            Long userB = (Long) sessionData.get("userB");

                            // Try to parse wrapper { sourceWsId, message }, fall back to raw body
                            try {
                                var root = mapper.readTree(body);
                                String sourceWsId = null;
                                String innerMessage = body;
                                if (root.has("sourceWsId") && root.has("message")) {
                                    sourceWsId = root.get("sourceWsId").asText();
                                    innerMessage = root.get("message").toString();
                                }
                                chatWebSocketHandler.sendToUser(userA, innerMessage, sourceWsId);
                                chatWebSocketHandler.sendToUser(userB, innerMessage, sourceWsId);
                            } catch (Exception ex) {
                                // if parsing fails, just forward raw body
                                chatWebSocketHandler.sendToUser(userA, body);
                                chatWebSocketHandler.sendToUser(userB, body);
                            }
                        }
                    } else if (channel.equals("user:requests")) {
                        var node = mapper.readTree(body);
                        if (node.has("toUserId")) {
                            Long target = node.get("toUserId").asLong();
                            chatWebSocketHandler.sendToUser(target, body);
                        }
                    }
                } catch (Exception e) {
                    // Silently ignore any processing errors
                }
            });
        } catch (Exception e) {
            // Silently ignore submission errors
        }
    }
}