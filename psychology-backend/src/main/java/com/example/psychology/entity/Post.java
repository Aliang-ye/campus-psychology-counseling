package com.example.psychology.entity;

import java.time.LocalDateTime;

public class Post {
    private Long id;
    private Long userId;
    private String username;
    private String role;
    private String title;
    private String content;
    private Boolean isPinned;
    private Boolean isLocked;
    private Integer replyCount;
    private String status; // pending(待审核), approved(已发布), rejected(已拒绝)
    private String auditComment; // 审核备注
    private Boolean isAnonymous;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 构造方法
    public Post() {}

    public Post(Long userId, String username, String role, String title, String content) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.title = title;
        this.content = content;
        this.isPinned = false;
        this.isLocked = false;
        this.replyCount = 0;
    }

    // Getter and Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Boolean getIsPinned() { return isPinned; }
    public void setIsPinned(Boolean isPinned) { this.isPinned = isPinned; }

    public Boolean getIsLocked() { return isLocked; }
    public void setIsLocked(Boolean isLocked) { this.isLocked = isLocked; }

    public Integer getReplyCount() { return replyCount; }
    public void setReplyCount(Integer replyCount) { this.replyCount = replyCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAuditComment() { return auditComment; }
    public void setAuditComment(String auditComment) { this.auditComment = auditComment; }

    public Boolean getIsAnonymous() { return isAnonymous; }
    public void setIsAnonymous(Boolean isAnonymous) { this.isAnonymous = isAnonymous; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
