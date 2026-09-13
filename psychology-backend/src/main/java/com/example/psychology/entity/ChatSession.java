package com.example.psychology.entity;

import java.sql.Timestamp;

public class ChatSession {
    private Long id;
    private Long initiatorId;
    private Long responderId;
    private String status; // PENDING, ACTIVE, CLOSED, REJECTED
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInitiatorId() { return initiatorId; }
    public void setInitiatorId(Long initiatorId) { this.initiatorId = initiatorId; }
    public Long getResponderId() { return responderId; }
    public void setResponderId(Long responderId) { this.responderId = responderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
