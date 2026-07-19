package com.edu.agent.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 智能体推荐资源的收藏快照，避免后续学习计划版本变化导致收藏失效。 */
@Entity
@Table(name = "resource_favorite", uniqueConstraints = {
        @UniqueConstraint(name = "uk_resource_favorite_collection_key", columnNames = {"collection_id", "resource_key"})
}, indexes = {
        @Index(name = "idx_resource_favorite_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_resource_favorite_collection", columnList = "collection_id,created_at")
})
public class ResourceFavorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "collection_id", nullable = false)
    private Long collectionId;
    @Column(name = "conversation_id", length = 64)
    private String conversationId;
    @Column(name = "resource_key", nullable = false, length = 64)
    private String resourceKey;
    @Column(nullable = false, length = 500)
    private String title;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;
    @Column(length = 120)
    private String platform;
    @Column(name = "resource_type", length = 40)
    private String resourceType;
    @Column(name = "course_key", length = 64)
    private String courseKey;
    @Column(name = "chapter_key", length = 64)
    private String chapterKey;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getCollectionId() { return collectionId; }
    public void setCollectionId(Long collectionId) { this.collectionId = collectionId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getResourceKey() { return resourceKey; }
    public void setResourceKey(String resourceKey) { this.resourceKey = resourceKey; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getCourseKey() { return courseKey; }
    public void setCourseKey(String courseKey) { this.courseKey = courseKey; }
    public String getChapterKey() { return chapterKey; }
    public void setChapterKey(String chapterKey) { this.chapterKey = chapterKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
