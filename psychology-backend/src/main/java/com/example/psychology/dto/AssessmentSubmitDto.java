package com.example.psychology.dto;

import java.util.Map;

public class AssessmentSubmitDto {
    private Long userId;
    private String username;
    private Map<String, Integer> answers; // questionId -> selectedOption (1-5)
    private Integer testDuration; // 测试时长（秒）

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

    public Map<String, Integer> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<String, Integer> answers) {
        this.answers = answers;
    }

    public Integer getTestDuration() {
        return testDuration;
    }

    public void setTestDuration(Integer testDuration) {
        this.testDuration = testDuration;
    }
}
