package com.example.psychology.entity;

import java.util.Date;

/**
 * 心理专家咨询申请
 */
public class ConsultRequest {
    private Long id;
    private Long userId;
    private String username;
    private String reason;  // 申请原因或描述
    private String status;  // PENDING（待处理）, APPROVED（已批准）, REJECTED（已拒绝）, CLOSED（已结束）
    private Long consultantId;  // 分配的咨询师ID
    private String consultantName;  // 咨询师名称
    private String notes;  // 咨询师备注
    private Date createdAt;
    private Date updatedAt;

    public ConsultRequest() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getConsultantId() {
        return consultantId;
    }

    public void setConsultantId(Long consultantId) {
        this.consultantId = consultantId;
    }

    public String getConsultantName() {
        return consultantName;
    }

    public void setConsultantName(String consultantName) {
        this.consultantName = consultantName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }
}
