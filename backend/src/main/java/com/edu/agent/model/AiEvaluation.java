package com.edu.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * AI 评价结果实体 —— 存储 AI 大模型对用户提交成果的评分与反馈
 *
 * 当用户提交任务成果（TaskSubmission）后，系统异步调用 AI 大模型（如 GPT-4o、DeepSeek 等），
 * 根据任务描述和用户提交的内容，从完成度、质量、创新性、规范性四个维度进行评分，
 * 并生成详细分析和改进建议。
 *
 * ========== 业务数据流 ==========
 *   用户提交成果 → TaskSubmission（status=PENDING）
 *     → SubmissionService.evaluateAsync() 异步调用 AI
 *       → AI 返回 JSON {score, analysis, suggestion}
 *       → 创建 AiEvaluation 持久化到 ai_evaluation 表
 *       → TaskSubmission.status→EVALUATED
 *
 * ========== 与 TaskSubmission 的关系 ==========
 *   task_submission  —1:1—→  ai_evaluation
 *   一个提交记录有且仅有一条 AI 评价结果（评价失败时无评价记录）
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Entity + @Table: JPA 实体注解，Spring Boot 自动扫描并映射到 MySQL 表
 *   - @Id + @GeneratedValue: JPA 主键策略（IDENTITY=MySQL 自增）
 *   - @Column: JPA 列映射（nullable/columnDefinition）
 *   - @PrePersist: JPA 生命周期回调（持久化前自动执行）
 *
 * ========== 表结构（由 JPA ddl-auto:update 自动创建） ==========
 *   ai_evaluation 表：
 *     id              BIGINT PRIMARY KEY AUTO_INCREMENT  — 主键
 *     submission_id   BIGINT NOT NULL                   — 关联提交记录 ID（唯一）
 *     score           INT NOT NULL                      — AI 评分（百分制 0-100）
 *     analysis        TEXT                              — AI 详细分析（优缺点评估）
 *     suggestion      TEXT                              — AI 改进建议
 *     evaluation_time DATETIME                           — 评价时间
 */
@Entity  // 【Spring Boot/JPA】声明为 JPA 实体 → Hibernate 映射到数据库表
@Table(name = "ai_evaluation")  // 【Spring Boot/JPA】指定映射的 MySQL 表名
public class AiEvaluation {

    /** 主键，自增 ID */
    @Id  // 【Spring Boot/JPA】标记主键字段
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 【Spring Boot/JPA】MySQL 自增主键策略
    private Long id;

    /** 关联的提交记录 ID（SubmissionService 中建立关联） */
    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    /** AI 评分（百分制，0-100），由 AI 根据任务描述和提交成果综合评定 */
    @Column(name = "score", nullable = false)
    private Integer score;

    /** AI 详细分析：从完成度、质量、创新性、规范性等维度进行文字评估 */
    @Column(name = "analysis", columnDefinition = "TEXT")
    private String analysis;

    /** AI 改进建议：具体的、可操作的建议，帮助用户提升学习效果 */
    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "weaknesses_json", columnDefinition = "TEXT")
    private String weaknessesJson;

    @Column(name = "recommended_actions_json", columnDefinition = "TEXT")
    private String recommendedActionsJson;

    /** 评价时间，由 @PrePersist 自动设置 */
    @Column(name = "evaluation_time")
    private LocalDateTime evaluationTime;

    /**
     * JPA 生命周期回调 —— 在持久化前自动设置评价时间
     * 仅在新增记录时触发（INSERT 时）
     */
    @PrePersist  // 【Spring Boot/JPA】实体持久化前的回调钩子
    protected void onCreate() {
        evaluationTime = LocalDateTime.now();
    }

    // ========== Getter / Setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

    public String getWeaknessesJson() { return weaknessesJson; }
    public void setWeaknessesJson(String weaknessesJson) { this.weaknessesJson = weaknessesJson; }

    public String getRecommendedActionsJson() { return recommendedActionsJson; }
    public void setRecommendedActionsJson(String recommendedActionsJson) { this.recommendedActionsJson = recommendedActionsJson; }

    public LocalDateTime getEvaluationTime() { return evaluationTime; }
    public void setEvaluationTime(LocalDateTime evaluationTime) { this.evaluationTime = evaluationTime; }
}
