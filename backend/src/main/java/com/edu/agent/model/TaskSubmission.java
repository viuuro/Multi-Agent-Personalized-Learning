package com.edu.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 任务成果提实体 —— 存储用户提交的任务完成成果
 *
 * 用户完成任务后，可以提交文本、图片链接、文件链接等成果，
 * 系统会异步调用 AI 大模型对成果进行自动评分（百分制），并返回详细分析与改进建议。
 *
 * ========== 业务数据流 ==========
 *   用户完成任务 → 提交成果（创建 TaskSubmission，status=PENDING）
 *     → AI 异步评价（SubmissionService.evaluateAsync()）
 *       → 成功：创建 AiEvaluation，status→EVALUATED
 *       → 失败：status→ERROR，记录 error_message
 *
 * ========== 状态流转 ==========
 *   PENDING  （待评价）—— 刚提交，AI 尚未完成评价
 *   EVALUATED（已评价）—— AI 评价完成，可在 ai_evaluation 表查询结果
 *   ERROR    （评价失败）—— AI 调用超时/格式错误，error_message 记录了失败原因
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Entity + @Table: JPA 实体注解，Spring Boot 自动扫描并映射到 MySQL 表
 *   - @Id + @GeneratedValue: JPA 主键策略（IDENTITY=MySQL 自增）
 *   - @Column: JPA 列映射（nullable/columnDefinition/length）
 *   - @PrePersist: JPA 生命周期回调（持久化前自动执行）
 *
 * ========== 表结构（由 JPA ddl-auto:update 自动创建） ==========
 *   task_submission 表：
 *     id              BIGINT PRIMARY KEY AUTO_INCREMENT  — 主键
 *     task_id         BIGINT NOT NULL                   — 关联任务 ID
 *     user_id         BIGINT NOT NULL                   — 提交者用户 ID
 *     content         TEXT NOT NULL                     — 提交的成果内容
 *     submission_time DATETIME                           — 提交时间
 *     status          VARCHAR(20) NOT NULL DEFAULT 'PENDING' — PENDING/EVALUATED/ERROR
 *     error_message   VARCHAR(1000)                      — AI 评价失败时的错误信息
 */
@Entity  // 【Spring Boot/JPA】声明为 JPA 实体 → Hibernate 映射到数据库表
@Table(name = "task_submission", uniqueConstraints = @UniqueConstraint(
        name = "uk_submission_task_version", columnNames = {"task_id", "version_number"}))
public class TaskSubmission {

    /** 提交状态：待评价（AI 尚未完成评价） */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    /** 提交状态：已评价（AI 已完成评价） */
    public static final String STATUS_EVALUATED = "EVALUATED";
    /** 提交状态：评价失败（AI 调用超时、返回格式错误等） */
    public static final String STATUS_ERROR = "ERROR";

    /** 主键，自增 ID */
    @Id  // 【Spring Boot/JPA】标记主键字段
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 【Spring Boot/JPA】MySQL 自增主键策略
    private Long id;

    /** 关联的任务 ID（SubmissionService.submit() 时从请求参数获取） */
    @Column(name = "task_id", nullable = false)
    private Long taskId;

    /** 提交者用户 ID（由服务器认证 Session 提供） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 所属对话，用于切换对话时恢复各自的成果与评分。 */
    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    // 保持数据库列可空以兼容升级前的历史提交；新提交始终由服务写入版本号。
    @Column(name = "version_number")
    private Integer versionNumber = 1;

    @Column(name = "previous_submission_id")
    private Long previousSubmissionId;

    @Column(name = "comparison_submission_id")
    private Long comparisonSubmissionId;

    /** 提交的成果内容（文本描述、链接等），AI 将此内容与任务描述对比进行评分 */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 提交时间，由 @PrePersist 自动设置 */
    @Column(name = "submission_time")
    private LocalDateTime submissionTime;

    /** 状态：PENDING（待评价）/ EVALUATED（已评价）/ ERROR（评价失败） */
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    /** AI 评价错误信息（当 status=ERROR 时记录失败原因，如 "AI API 调用超时"） */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    /**
     * JPA 生命周期回调 —— 在持久化前自动设置提交时间
     * 仅在新增记录时触发（INSERT 时）
     */
    @PrePersist  // 【Spring Boot/JPA】实体持久化前的回调钩子
    protected void onCreate() {
        submissionTime = LocalDateTime.now();
    }

    // ========== Getter / Setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public Long getPreviousSubmissionId() { return previousSubmissionId; }
    public void setPreviousSubmissionId(Long previousSubmissionId) { this.previousSubmissionId = previousSubmissionId; }

    public Long getComparisonSubmissionId() { return comparisonSubmissionId; }
    public void setComparisonSubmissionId(Long comparisonSubmissionId) { this.comparisonSubmissionId = comparisonSubmissionId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getSubmissionTime() { return submissionTime; }
    public void setSubmissionTime(LocalDateTime submissionTime) { this.submissionTime = submissionTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getProcessingStartedAt() { return processingStartedAt; }
    public void setProcessingStartedAt(LocalDateTime processingStartedAt) { this.processingStartedAt = processingStartedAt; }
}
