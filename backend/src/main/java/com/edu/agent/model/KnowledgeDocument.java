package com.edu.agent.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 知识库文档元数据；正文按可检索分块存放在 knowledge_chunk。 */
@Entity
@Table(name = "knowledge_document", indexes = {
        @Index(name = "idx_kd_user_scope", columnList = "user_id,scope"),
        @Index(name = "idx_kd_conversation", columnList = "conversation_id"),
        @Index(name = "idx_kd_checksum", columnList = "checksum")
})
public class KnowledgeDocument {

    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_USER = "USER";
    public static final String SCOPE_CONVERSATION = "CONVERSATION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(nullable = false, length = 20)
    private String scope;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "source_uri", length = 1000)
    private String sourceUri;

    @Column(length = 255)
    private String license;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "character_count", nullable = false)
    private Integer characterCount;

    @Column(name = "chunk_count", nullable = false)
    private Integer chunkCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "READY";
        if (characterCount == null) characterCount = 0;
        if (chunkCount == null) chunkCount = 0;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceUri() { return sourceUri; }
    public void setSourceUri(String sourceUri) { this.sourceUri = sourceUri; }
    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCharacterCount() { return characterCount; }
    public void setCharacterCount(Integer characterCount) { this.characterCount = characterCount; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
