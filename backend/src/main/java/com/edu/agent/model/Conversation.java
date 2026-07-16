package com.edu.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 对话记录实体 —— 持久化到 MySQL conversation 表
 *
 * 每次用户与 AI 的对话都会保存到此表，包含角色、内容、时间戳等。
 * 通过 conversation_id 将多轮对话关联到同一个会话中。
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Entity + @Table: JPA 实体映射
 *   - @PrePersist: JPA 生命周期回调（持久化前自动执行）
 *   - JpaRepository: Spring Data JPA 提供 CRUD（见 ConversationRepository）
 */
@Entity  // 【Spring Boot/JPA】JPA 实体 → Hibernate 管理生命周期
@Table(name = "conversation")  // 【Spring Boot/JPA】映射到 MySQL conversation 表
public class Conversation {

    /** 主键，自增 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 消息内容，使用 TEXT 类型以支持长文本 */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** AI 上下文使用的完整内容（例如文件解析文本），不返回给前端展示。 */
    @JsonIgnore
    @Column(name = "ai_content", columnDefinition = "MEDIUMTEXT")
    private String aiContent;

    /** 消息角色：user（用户）或 assistant（AI助手） */
    @Column(length = 50)
    private String role;

    /** 所属用户 ID，用于多用户数据隔离 */
    @Column(name = "user_id")
    private Long userId;

    /** 会话 ID，同一轮对话共享同一个 ID，用于关联多条消息 */
    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    /** 附件名称；普通文本消息为空。 */
    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    /** 附件类型：image/file。 */
    @Column(name = "attachment_type", length = 20)
    private String attachmentType;

    /** 图片使用 Data URL 保存，刷新后仍可恢复预览。 */
    @Column(name = "attachment_data", columnDefinition = "MEDIUMTEXT")
    private String attachmentData;

    /** 由会话元数据表补充，仅用于 API 返回。 */
    @Transient
    private String conversationTitle;

    /** 消息时间戳，不可为空 */
    @Column(nullable = false)
    private LocalDateTime timestamp;

    /**
     * JPA 生命周期回调 —— 在持久化前自动设置时间戳
     * 如果业务代码已显式设置 timestamp，则保留业务代码的值
     */
    @PrePersist  // 【Spring Boot/JPA】实体持久化前的回调钩子
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    // ========== Getter / Setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getAiContent() { return aiContent; }
    public void setAiContent(String aiContent) { this.aiContent = aiContent; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }

    public String getAttachmentType() { return attachmentType; }
    public void setAttachmentType(String attachmentType) { this.attachmentType = attachmentType; }

    public String getAttachmentData() { return attachmentData; }
    public void setAttachmentData(String attachmentData) { this.attachmentData = attachmentData; }

    public String getConversationTitle() { return conversationTitle; }
    public void setConversationTitle(String conversationTitle) { this.conversationTitle = conversationTitle; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
