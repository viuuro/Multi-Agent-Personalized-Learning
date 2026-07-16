package com.edu.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 对话级元数据，保存独立标题并避免依赖浏览器 localStorage。 */
@Entity
@Table(
        name = "conversation_session",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversation_session_user_conversation",
                columnNames = {"user_id", "conversation_id"})
)
public class ConversationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "title", nullable = false, length = 120)
    private String title = "新对话";

    /** 长期目标、重要约束、进展和未解决问题组成的滚动摘要。 */
    @Column(name = "memory_summary", columnDefinition = "MEDIUMTEXT")
    private String memorySummary;

    /** 仅在当前阶段有效的精力、情绪、时间和待澄清动作。 */
    @Column(name = "temporary_state_json", columnDefinition = "TEXT")
    private String temporaryStateJson;

    @Column(name = "last_intent", length = 40)
    private String lastIntent;

    @Column(name = "dialogue_state", length = 40)
    private String dialogueState;

    @Column(name = "pending_question", length = 500)
    private String pendingQuestion;

    @Column(name = "last_quality_json", columnDefinition = "TEXT")
    private String lastQualityJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMemorySummary() { return memorySummary; }
    public void setMemorySummary(String memorySummary) { this.memorySummary = memorySummary; }
    public String getTemporaryStateJson() { return temporaryStateJson; }
    public void setTemporaryStateJson(String temporaryStateJson) { this.temporaryStateJson = temporaryStateJson; }
    public String getLastIntent() { return lastIntent; }
    public void setLastIntent(String lastIntent) { this.lastIntent = lastIntent; }
    public String getDialogueState() { return dialogueState; }
    public void setDialogueState(String dialogueState) { this.dialogueState = dialogueState; }
    public String getPendingQuestion() { return pendingQuestion; }
    public void setPendingQuestion(String pendingQuestion) { this.pendingQuestion = pendingQuestion; }
    public String getLastQualityJson() { return lastQualityJson; }
    public void setLastQualityJson(String lastQualityJson) { this.lastQualityJson = lastQualityJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
