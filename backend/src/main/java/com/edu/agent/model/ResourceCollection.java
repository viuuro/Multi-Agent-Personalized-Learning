package com.edu.agent.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 用户可维护的学习资源收藏夹；系统课程收藏夹与自定义收藏夹使用同一套结构。 */
@Entity
@Table(name = "resource_collection", uniqueConstraints = {
        @UniqueConstraint(name = "uk_resource_collection_user_name", columnNames = {"user_id", "name"})
}, indexes = {
        @Index(name = "idx_resource_collection_user_updated", columnList = "user_id,updated_at")
})
public class ResourceCollection {
    public static final String KIND_COURSE = "COURSE";
    public static final String KIND_CUSTOM = "CUSTOM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false, length = 20)
    private String kind = KIND_CUSTOM;
    @Column(name = "course_key", length = 64)
    private String courseKey;
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

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getCourseKey() { return courseKey; }
    public void setCourseKey(String courseKey) { this.courseKey = courseKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
