package com.example.psychology.service;

import com.example.psychology.common.ApiResponse;
import com.example.psychology.dto.LoginDto;
import com.example.psychology.dto.RegisterDto;
import com.example.psychology.entity.User;
import com.example.psychology.repository.UserRepository;
import com.example.psychology.security.AuthUser;
import com.example.psychology.security.JwtService;
import com.example.psychology.security.PasswordHasher;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {
    private static final String DEFAULT_ROLE = "student";

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordHasher passwordHasher, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
    }

    public Map<String, Object> register(RegisterDto dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getPassword() == null || dto.getPassword().isBlank()) {
            return ApiResponse.fail("用户名或密码不能为空");
        }
        if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
            return ApiResponse.fail("用户名已存在");
        }
        userRepository.insert(dto.getUsername(), passwordHasher.hash(dto.getPassword()), DEFAULT_ROLE);
        User user = userRepository.findByUsername(dto.getUsername()).orElse(null);
        if (user == null) {
            return ApiResponse.fail("注册失败：无法获取用户信息");
        }
        Map<String, Object> result = authPayload(user, DEFAULT_ROLE);
        result.put("message", "注册成功");
        return result;
    }

    public Map<String, Object> login(LoginDto dto) {
        if (dto.getUsername() == null || dto.getPassword() == null || dto.getRole() == null) {
            return ApiResponse.fail("用户名、密码或角色不能为空");
        }
        User user = userRepository.findByUsernameAndRole(dto.getUsername(), dto.getRole()).orElse(null);
        if (user == null || !passwordHasher.matches(dto.getPassword(), user.getPassword())) {
            return ApiResponse.fail("用户名或密码错误，或角色不匹配");
        }
        if (!passwordHasher.isHashed(user.getPassword())) {
            userRepository.updatePassword(user.getId(), passwordHasher.hash(dto.getPassword()));
        }
        return authPayload(user, user.getRole());
    }

    private Map<String, Object> authPayload(User user, String role) {
        AuthUser authUser = new AuthUser(user.getId(), user.getUsername(), role);
        Map<String, Object> result = ApiResponse.success();
        result.put("userId", user.getId());
        result.put("role", role);
        result.put("username", user.getUsername());
        result.put("token", jwtService.issue(authUser));
        return result;
    }
}
