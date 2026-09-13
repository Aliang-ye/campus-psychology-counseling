package com.example.psychology.websocket;

import com.example.psychology.security.AuthContext;
import com.example.psychology.security.ForbiddenException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ws/debug")
public class WebSocketDebugController {

    @Autowired
    private ChatWebSocketHandler chatWebSocketHandler;

    @GetMapping("/sessions")
    public Map<String, Object> sessions() {
        if (!AuthContext.require().isAdmin()) {
            throw new ForbiddenException("无权访问调试接口");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("sessionIdToUserId", chatWebSocketHandler.getSessionIdToUserIdSnapshot());
        result.put("sessionsByUser", chatWebSocketHandler.getSessionsByUserSnapshot());
        return result;
    }
}
