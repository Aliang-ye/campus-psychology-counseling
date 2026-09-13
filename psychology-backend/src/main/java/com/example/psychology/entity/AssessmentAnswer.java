package com.example.psychology.entity;

import java.util.Date;

public class AssessmentAnswer {
    private Long id;
    private Long recordId;
    private String questionId;
    private Integer answerOption;
    private Integer score;
    private Date createdAt;

    public AssessmentAnswer() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void setQuestionId(String questionId) {
        this.questionId = questionId;
    }

    public Integer getAnswerOption() {
        return answerOption;
    }

    public void setAnswerOption(Integer answerOption) {
        this.answerOption = answerOption;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}
