package com.edu.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户画像实体 —— 6 维度学习画像
 *
 * 画像维度：
 *   1. knowledgeBase   - 知识基础评分 (1-10)
 *   2. cognitiveStyle  - 学习风格 (visual/verbal/kinesthetic)
 *   3. weaknessPoints  - 薄弱点列表
 *   4. learningPace    - 学习节奏评分 (1-10)
 *   5. interestAreas   - 兴趣领域列表
 *   6. shortTermGoal   - 短期学习目标
 *
 * 数据库存储使用 MySQL JSON 类型（profile_json 字段），
 * Java 代码通过 @Transient 字段方便读写，持久化时序列化到 JSON。
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Entity + @Table: JPA 注解，Spring Boot 自动扫描并映射到 MySQL 表
 *   - @Id + @GeneratedValue: JPA 主键策略（IDENTITY=自增）
 *   - @Column: JPA 列映射（name/columnDefinition/length）
 *   - @Transient: JPA 忽略字段（不映射数据库列，仅 Java 内存使用）
 */
@Entity  // 【Spring Boot/JPA】声明为 JPA 实体 → Hibernate 映射到数据库表
@Table(name = "user_profile")  // 【Spring Boot/JPA】指定映射的 MySQL 表名
public class UserProfile {

    /** 主键，自增 ID */
    @Id  // 【Spring Boot/JPA】标记主键字段
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 【Spring Boot/JPA】MySQL 自增主键策略
    private Long id;

    /** 所属用户 ID，用于多用户画像隔离 */
    @Column(name = "user_id")
    private Long userId;

    /** 所属对话 ID，同一用户的不同对话拥有独立画像 */
    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    /** 画像 JSON 数据，直接存入 MySQL JSON 类型字段 */
    @Column(name = "profile_json", columnDefinition = "JSON")
    private String profileJson;

    /** 画像最后更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== 【Spring Boot/JPA】@Transient 字段：仅存在于 Java 内存，不映射数据库列 =====

    /** 知识基础评分：1（入门） - 10（专家） */
    @Transient
    private Integer knowledgeBase = 5;

    /** 学习风格：visual（视觉型）、verbal（语言型）、kinesthetic（动手实践型） */
    @Transient
    private String cognitiveStyle = "verbal";

    /** 薄弱点：学习中的易错领域/知识盲区 */
    @Transient
    private List<String> weaknessPoints = List.of();

    /** 学习节奏评分：1（慢速深入） - 10（快速浏览） */
    @Transient
    private Integer learningPace = 5;

    /** 兴趣领域：学生感兴趣的知识方向 */
    @Transient
    private List<String> interestAreas = List.of();

    /** 短期目标：一句话描述当前阶段的学习目标 */
    @Transient
    private String shortTermGoal = "";

    // ========== Getter / Setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getProfileJson() { return profileJson; }
    public void setProfileJson(String profileJson) { this.profileJson = profileJson; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Integer getKnowledgeBase() { return knowledgeBase; }
    public void setKnowledgeBase(Integer knowledgeBase) { this.knowledgeBase = knowledgeBase; }

    public String getCognitiveStyle() { return cognitiveStyle; }
    public void setCognitiveStyle(String cognitiveStyle) { this.cognitiveStyle = cognitiveStyle; }

    public List<String> getWeaknessPoints() { return weaknessPoints; }
    public void setWeaknessPoints(List<String> weaknessPoints) { this.weaknessPoints = weaknessPoints; }

    public Integer getLearningPace() { return learningPace; }
    public void setLearningPace(Integer learningPace) { this.learningPace = learningPace; }

    public List<String> getInterestAreas() { return interestAreas; }
    public void setInterestAreas(List<String> interestAreas) { this.interestAreas = interestAreas; }

    public String getShortTermGoal() { return shortTermGoal; }
    public void setShortTermGoal(String shortTermGoal) { this.shortTermGoal = shortTermGoal; }
}
