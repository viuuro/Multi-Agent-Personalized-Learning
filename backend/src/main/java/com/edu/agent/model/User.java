package com.edu.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 用户实体 —— 持久化到 MySQL app_user 表
 *
 * 存储用户的基本信息：用户名、BCrypt 密码摘要、头像（Base64 编码）。
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Entity + @Table: JPA 实体注解，Spring Boot 自动扫描并映射到 MySQL 表
 *   - @Id + @GeneratedValue: JPA 主键策略（IDENTITY=MySQL 自增）
 *   - @Column: JPA 列映射（unique/nullable/length/columnDefinition）
 *   - @PrePersist / @PreUpdate: JPA 生命周期回调（持久化前/更新前自动执行）
 */
@Entity  // 【Spring Boot/JPA】声明为 JPA 实体 → Hibernate 映射到数据库表
@Table(name = "app_user")  // 【Spring Boot/JPA】指定映射的 MySQL 表名
public class User {

    /** 主键，自增 ID */
    @Id  // 【Spring Boot/JPA】标记主键字段
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 【Spring Boot/JPA】MySQL 自增主键策略
    private Long id;

    /** 用户名，唯一约束，不允许为空 */
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    /** BCrypt 密码摘要；旧 SHA-256 摘要会在成功登录后自动迁移。 */
    @Column(nullable = false, length = 128)
    private String password;

    /** 头像数据（Base64 编码的图片），使用 MEDIUMTEXT 类型支持较大图片 */
    @Column(columnDefinition = "MEDIUMTEXT")
    private String avatar;

    /** 记录创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 记录最后更新时间 */
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

    // ========== Getter / Setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
