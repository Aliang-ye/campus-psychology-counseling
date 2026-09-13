package com.example.psychology.dto;

public class PostDto {
    private String title;
    private String content;
    private Boolean isAnonymous;

    public PostDto() {}

    public PostDto(String title, String content) {
        this.title = title;
        this.content = content;
        this.isAnonymous = false;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Boolean getIsAnonymous() { return isAnonymous; }
    public void setIsAnonymous(Boolean isAnonymous) { this.isAnonymous = isAnonymous; }
}
