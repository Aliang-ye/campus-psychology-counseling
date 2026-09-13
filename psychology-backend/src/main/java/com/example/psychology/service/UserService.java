package com.example.psychology.service;

import com.example.psychology.common.ApiResponse;
import com.example.psychology.entity.User;
import com.example.psychology.repository.UserRepository;
import com.example.psychology.security.AuthUser;
import com.example.psychology.security.ForbiddenException;
import com.example.psychology.security.PasswordHasher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JdbcTemplate jdbcTemplate;

    public UserService(UserRepository userRepository, PasswordHasher passwordHasher, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getProfile(AuthUser currentUser) {
        User user = userRepository.findById(currentUser.getUserId()).orElse(null);
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("role", user.getRole());
        return ApiResponse.success("user", userMap);
    }

    public Map<String, Object> changePassword(AuthUser currentUser, String oldPassword, String newPassword) {
        if (oldPassword == null || newPassword == null || newPassword.isBlank()) {
            return ApiResponse.fail("参数不完整");
        }
        User user = userRepository.findById(currentUser.getUserId()).orElse(null);
        if (user == null || !passwordHasher.matches(oldPassword, user.getPassword())) {
            return ApiResponse.fail("原密码错误或用户不存在");
        }
        userRepository.updatePassword(currentUser.getUserId(), passwordHasher.hash(newPassword));
        return ApiResponse.success();
    }

    public Map<String, Object> listVisibleUsers(AuthUser currentUser) {
        if (!currentUser.isStaff()) {
            throw new ForbiddenException("无权访问用户列表");
        }
        Object[] users = userRepository.findStaffVisibleUsers().stream().map(u -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", u.getId());
            item.put("username", u.getUsername());
            item.put("role", u.getRole());
            return item;
        }).toArray();
        return ApiResponse.success("users", users);
    }

    public Map<String, Object> adminUpdateUser(AuthUser currentUser, Map<String, String> body) {
        if (!currentUser.isAdmin()) {
            throw new ForbiddenException("只有管理员可以修改他人信息");
        }
        String targetIdStr = body.get("targetId");
        if (targetIdStr == null) {
            return ApiResponse.fail("参数不完整");
        }
        Long targetId;
        try {
            targetId = Long.valueOf(targetIdStr);
        } catch (NumberFormatException e) {
            return ApiResponse.fail("目标用户ID不合法");
        }

        StringBuilder sql = new StringBuilder("UPDATE user SET ");
        List<Object> params = new ArrayList<>();
        String newUsername = body.get("username");
        String newPassword = body.get("password");
        String newRole = body.get("role");
        if (newUsername != null && !newUsername.isEmpty()) {
            sql.append("username=?,");
            params.add(newUsername);
        }
        if (newPassword != null && !newPassword.isEmpty()) {
            sql.append("password=?,");
            params.add(passwordHasher.hash(newPassword));
        }
        if (newRole != null && !newRole.isEmpty()) {
            sql.append("role=?,");
            params.add(newRole);
        }
        if (params.isEmpty()) {
            return ApiResponse.fail("没有需要更新的字段");
        }
        sql.setLength(sql.length() - 1);
        sql.append(" WHERE id=?");
        params.add(targetId);
        int updated = jdbcTemplate.update(sql.toString(), params.toArray());
        if (updated == 0) {
            return ApiResponse.fail("目标用户不存在");
        }
        return ApiResponse.success();
    }
}
