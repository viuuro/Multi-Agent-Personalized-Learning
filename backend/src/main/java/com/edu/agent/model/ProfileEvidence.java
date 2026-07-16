package com.edu.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 学习画像事实的证据、可信度、作用域与冲突状态。 */
@Entity
@Table(name = "profile_evidence", indexes = {
        @Index(name = "idx_profile_evidence_owner", columnList = "user_id,conversation_id,dimension,status")
})
public class ProfileEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "dimension", nullable = false, length = 50)
    private String dimension;

    @Column(name = "value_json", nullable = false, columnDefinition = "TEXT")
    private String valueJson;

    @Column(name = "evidence_text", nullable = false, length = 1000)
    private String evidenceText;

    @Column(name = "source_message_id")
    private Long sourceMessageId;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    /** LONG_TERM / SHORT_TERM */
    @Column(name = "scope", nullable = false, length = 20)
    private String scope;

    /** ACTIVE / SUPERSEDED / CONFLICT */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }
    public String getValueJson() { return valueJson; }
    public void setValueJson(String valueJson) { this.valueJson = valueJson; }
    public String getEvidenceText() { return evidenceText; }
    public void setEvidenceText(String evidenceText) { this.evidenceText = evidenceText; }
    public Long getSourceMessageId() { return sourceMessageId; }
    public void setSourceMessageId(Long sourceMessageId) { this.sourceMessageId = sourceMessageId; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
