package com.edu.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 学习计划持久化实体
 *
 * 将 AI 生成的 / 用户编辑后的学习计划以 JSON 形式存入 MySQL，
 * 使计划在刷新页面、重新登录后仍然可用。
 */
@Entity
@Table(name = "learning_plan")
public class LearningPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联用户 ID（应用层隔离，不设 FK 约束） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 所属对话 ID，用于隔离同一用户的不同学习计划 */
    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    /** 完整计划 JSON（weeks 数组 + resources） */
    @Column(name = "plan_json", columnDefinition = "TEXT", nullable = false)
    private String planJson;

    /** 同一对话内递增的计划版本号。 */
    @Column(name = "version_number", nullable = false, columnDefinition = "integer default 1")
    private Integer versionNumber = 1;

    /** create/manual_edit/modify_week/adjust_difficulty 等。 */
    @Column(name = "revision_type", length = 40)
    private String revisionType;

    @Column(name = "revision_reason", length = 1000)
    private String revisionReason;

    @Column(name = "parent_plan_id")
    private Long parentPlanId;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 最后更新时间（编辑后刷新） */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public String getRevisionType() { return revisionType; }
    public void setRevisionType(String revisionType) { this.revisionType = revisionType; }

    public String getRevisionReason() { return revisionReason; }
    public void setRevisionReason(String revisionReason) { this.revisionReason = revisionReason; }

    public Long getParentPlanId() { return parentPlanId; }
    public void setParentPlanId(Long parentPlanId) { this.parentPlanId = parentPlanId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
