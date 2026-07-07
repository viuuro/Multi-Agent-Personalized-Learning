package com.edu.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 任务实体 —— 学习计划中的具体任务
 *
 * 每个任务关联一个学习计划（LearningPlanEntity），包含任务描述和状态。
 * 用户完成任务后可通过 TaskSubmission 提交成果，由 AI 进行评分和分析。
 *
 * ========== 数据流 ==========
 *   学习计划生成 → 计划中包含多个 Task → 用户完成任务 → 提交成果（TaskSubmission）
 *     → AI 自动评分（AiEvaluation）→ 用户查看评分与改进建议
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Entity + @Table: JPA 实体注解，Spring Boot 自动扫描并映射到 MySQL 表
 *   - @Id + @GeneratedValue: JPA 主键策略（IDENTITY=MySQL 自增）
 *   - @Column: JPA 列映射（nullable/length）
 *   - @PrePersist / @PreUpdate: JPA 生命周期回调（持久化前/更新前自动执行）
 *
 * ========== 表结构（由 JPA ddl-auto:update 自动创建） ==========
 *   learning_task 表：
 *     id          BIGINT PRIMARY KEY AUTO_INCREMENT  — 主键
 *     user_id     BIGINT NOT NULL                   — 关联用户
 *     plan_id     BIGINT NOT NULL                   — 关联学习计划
 *     description VARCHAR(1000) NOT NULL             — 任务描述
 *     status      VARCHAR(20) NOT NULL DEFAULT 'PENDING' — PENDING/COMPLETED
 *     created_at  DATETIME                           — 创建时间
 *     updated_at  DATETIME                           — 更新时间
 */
@Entity  // 【Spring Boot/JPA】声明为 JPA 实体 → Hibernate 映射到数据库表
@Table(name = "learning_task")  // 【Spring Boot/JPA】指定映射的 MySQL 表名
public class Task {

    /** 主键，自增 ID */
    @Id  // 【Spring Boot/JPA】标记主键字段
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 【Spring Boot/JPA】MySQL 自增主键策略
    private Long id;

    /** 关联用户 ID（应用层隔离，不设 FK 约束） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 所属计划 ID（关联 LearningPlanEntity，不设 FK 约束） */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /** 任务描述（如 "完成 Python 基础语法学习"），AI 评分时会参考此描述判断完成度 */
    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    /** 任务状态：PENDING（待完成）、COMPLETED（已完成） */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * JPA 生命周期回调 —— 在持久化前自动设置创建时间和更新时间
     * 仅在新增记录时触发（INSERT 时）
     */
    @PrePersist  // 【Spring Boot/JPA】实体持久化前的回调钩子
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * JPA 生命周期回调 —— 在更新前自动设置更新时间
     * 仅在更新记录时触发（UPDATE 时）
     */
    @PreUpdate  // 【Spring Boot/JPA】实体更新前的回调钩子
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}