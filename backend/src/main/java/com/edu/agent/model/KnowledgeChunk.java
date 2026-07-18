package com.edu.agent.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 可检索的知识正文分块，冗余来源字段以减少检索阶段的关联查询。 */
@Entity
@Table(name = "knowledge_chunk", indexes = {
        @Index(name = "idx_kc_document", columnList = "document_id,chunk_index"),
        @Index(name = "idx_kc_user_scope", columnList = "user_id,scope"),
        @Index(name = "idx_kc_conversation", columnList = "conversation_id")
})
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(nullable = false, length = 20)
    private String scope;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(length = 500)
    private String heading;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "document_title", nullable = false, length = 255)
    private String documentTitle;

    @Column(name = "source_uri", length = 1000)
    private String sourceUri;

    @Column(length = 255)
    private String license;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getHeading() { return heading; }
    public void setHeading(String heading) { this.heading = heading; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDocumentTitle() { return documentTitle; }
    public void setDocumentTitle(String documentTitle) { this.documentTitle = documentTitle; }
    public String getSourceUri() { return sourceUri; }
    public void setSourceUri(String sourceUri) { this.sourceUri = sourceUri; }
    public String getLicense() { return license; }
    public void setLicense(String license) { this.license = license; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
