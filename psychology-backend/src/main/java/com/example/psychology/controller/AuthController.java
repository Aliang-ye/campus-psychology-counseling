package com.example.psychology.controller;

import com.example.psychology.dto.LoginDto;
import com.example.psychology.dto.RegisterDto;
import com.example.psychology.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterDto dto) {
        return authService.register(dto);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginDto dto) {
        return authService.login(dto);
    }
}
