package com.edu.agent.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 用户对具体学习资源的点击和显式评价，用于后续个性化排序。 */
@Entity
@Table(name = "learning_resource_feedback", uniqueConstraints = {
        @UniqueConstraint(name = "uk_resource_feedback_user_key", columnNames = {"user_id", "resource_key"})
}, indexes = {
        @Index(name = "idx_resource_feedback_user_updated", columnList = "user_id,updated_at"),
        @Index(name = "idx_resource_feedback_conversation", columnList = "user_id,conversation_id")
})
public class LearningResourceFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "conversation_id", length = 64)
    private String conversationId;
    @Column(name = "resource_key", nullable = false, length = 64)
    private String resourceKey;
    @Column(name = "resource_url", nullable = false, columnDefinition = "TEXT")
    private String resourceUrl;
    @Column(name = "resource_title", length = 500)
    private String resourceTitle;
    @Column(name = "click_count", nullable = false)
    private Integer clickCount = 0;
    @Column(name = "helpful_score", nullable = false)
    private Integer helpfulScore = 0;
    @Column(name = "completed_count", nullable = false)
    private Integer completedCount = 0;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getResourceKey() { return resourceKey; }
    public void setResourceKey(String resourceKey) { this.resourceKey = resourceKey; }
    public String getResourceUrl() { return resourceUrl; }
    public void setResourceUrl(String resourceUrl) { this.resourceUrl = resourceUrl; }
    public String getResourceTitle() { return resourceTitle; }
    public void setResourceTitle(String resourceTitle) { this.resourceTitle = resourceTitle; }
    public Integer getClickCount() { return clickCount; }
    public void setClickCount(Integer clickCount) { this.clickCount = clickCount; }
    public Integer getHelpfulScore() { return helpfulScore; }
    public void setHelpfulScore(Integer helpfulScore) { this.helpfulScore = helpfulScore; }
    public Integer getCompletedCount() { return completedCount; }
    public void setCompletedCount(Integer completedCount) { this.completedCount = completedCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
