package com.example.psychology.security;

public class AuthUser {
    private final Long userId;
    private final String username;
    private final String role;

    public AuthUser(Long userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role == null ? "" : role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin() {
        return "admin".equals(role);
    }

    public boolean isCounselor() {
        return "doctor".equals(role) || "teacher".equals(role);
    }

    public boolean isStaff() {
        return isAdmin() || isCounselor();
    }
}
