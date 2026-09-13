package com.example.psychology.controller;

import com.example.psychology.security.AuthContext;
import com.example.psychology.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = "*")
public class ProfileController {
    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public Map<String, Object> getMyProfile() {
        return userService.getProfile(AuthContext.require());
    }

    @PostMapping("/updateMe")
    public Map<String, Object> updateMyProfile(@RequestBody Map<String, String> body) {
        return userService.changePassword(
                AuthContext.require(),
                body.get("oldPassword"),
                body.get("newPassword"));
    }

    @GetMapping("/users")
    public Map<String, Object> listUsers() {
        return userService.listVisibleUsers(AuthContext.require());
    }

    @PostMapping("/admin/updateUser")
    public Map<String, Object> adminUpdateUser(@RequestBody Map<String, String> body) {
        return userService.adminUpdateUser(AuthContext.require(), body);
    }
}
