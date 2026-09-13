package com.example.psychology.entity;

import java.time.LocalDateTime;

public class Reply {
    private Long id;
    private Long postId;
    private Long userId;
    private String username;
    private String role;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 构造方法
    public Reply() {}

    public Reply(Long postId, Long userId, String username, String role, String content) {
        this.postId = postId;
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.content = content;
    }

    // Getter and Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
