package com.example.psychology.entity;

import java.util.Date;

public class AssessmentRecord {
    private Long id;
    private Long userId;
    private String username;
    private Integer totalScore;
    private Integer dimensionAScore;
    private Integer dimensionBScore;
    private Integer dimensionCScore;
    private Integer dimensionDScore;
    private Integer dimensionEScore;
    private Integer testDuration;
    private String adviceOverall;
    private String adviceByDimension;
    private Date createdAt;

    public AssessmentRecord() {}

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

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public Integer getDimensionAScore() {
        return dimensionAScore;
    }

    public void setDimensionAScore(Integer dimensionAScore) {
        this.dimensionAScore = dimensionAScore;
    }

    public Integer getDimensionBScore() {
        return dimensionBScore;
    }

    public void setDimensionBScore(Integer dimensionBScore) {
        this.dimensionBScore = dimensionBScore;
    }

    public Integer getDimensionCScore() {
        return dimensionCScore;
    }

    public void setDimensionCScore(Integer dimensionCScore) {
        this.dimensionCScore = dimensionCScore;
    }

    public Integer getDimensionDScore() {
        return dimensionDScore;
    }

    public void setDimensionDScore(Integer dimensionDScore) {
        this.dimensionDScore = dimensionDScore;
    }

    public Integer getDimensionEScore() {
        return dimensionEScore;
    }

    public void setDimensionEScore(Integer dimensionEScore) {
        this.dimensionEScore = dimensionEScore;
    }

    public Integer getTestDuration() {
        return testDuration;
    }

    public void setTestDuration(Integer testDuration) {
        this.testDuration = testDuration;
    }

    public String getAdviceOverall() {
        return adviceOverall;
    }

    public void setAdviceOverall(String adviceOverall) {
        this.adviceOverall = adviceOverall;
    }

    public String getAdviceByDimension() {
        return adviceByDimension;
    }

    public void setAdviceByDimension(String adviceByDimension) {
        this.adviceByDimension = adviceByDimension;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
