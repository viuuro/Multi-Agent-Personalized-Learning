package com.edu.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 已解析/提交文件及聊天图片的持久化元数据；不重复保存原始文件内容。 */
@Entity
@Table(name = "uploaded_file_record")
public class UploadedFileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /** CHAT / SUBMISSION / KNOWLEDGE */
    @Column(name = "purpose", nullable = false, length = 20)
    private String purpose;

    /** DOCUMENT / IMAGE；由历史文件接口统一补充，不产生额外二进制副本。 */
    @Transient
    private String fileKind;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getFileKind() { return fileKind; }
    public void setFileKind(String fileKind) { this.fileKind = fileKind; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
