package com.ncf.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "news_post")
public class NewsPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String visibility;

    private String category;

    private String targetScope;

    private Long targetFamilyId;

    private String targetFamilyName;

    private Long publisherId;

    private String publisherName;

    private Instant publishTime;

    @Column(columnDefinition = "TEXT")
    private String attachments;

    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTargetScope() {
        return targetScope;
    }

    public void setTargetScope(String targetScope) {
        this.targetScope = targetScope;
    }

    public Long getTargetFamilyId() {
        return targetFamilyId;
    }

    public void setTargetFamilyId(Long targetFamilyId) {
        this.targetFamilyId = targetFamilyId;
    }

    public String getTargetFamilyName() {
        return targetFamilyName;
    }

    public void setTargetFamilyName(String targetFamilyName) {
        this.targetFamilyName = targetFamilyName;
    }

    public Long getPublisherId() { return publisherId; }
    public void setPublisherId(Long publisherId) { this.publisherId = publisherId; }

    public String getPublisherName() { return publisherName; }
    public void setPublisherName(String publisherName) { this.publisherName = publisherName; }

    public Instant getPublishTime() { return publishTime; }
    public void setPublishTime(Instant publishTime) { this.publishTime = publishTime; }

    public String getAttachments() { return attachments; }
    public void setAttachments(String attachments) { this.attachments = attachments; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
