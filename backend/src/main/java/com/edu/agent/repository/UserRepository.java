package com.edu.agent.repository;

import com.edu.agent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层
 *
 * 提供用户的 CRUD 操作和自定义查询方法。
 * 所有方法由 Spring Data JPA 在运行时自动生成 SQL 实现。
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Repository: Spring Data JPA 仓库标记，自动生成实现类
 *   - extends JpaRepository<User, Long>: 继承获得 CRUD 方法
 *     自动提供: save(), findById(), findAll(), delete(), count() 等
 *   - 方法命名查询: Spring Data JPA 根据方法名自动生成 SQL
 *     findByUsername → SELECT * FROM app_user WHERE username = ?
 *     existsByUsername → SELECT COUNT(*) > 0 FROM app_user WHERE username = ?
 */
@Repository  // 【Spring Boot/Data JPA】标记为数据访问仓库，启动时自动扫描并生成代理实现
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据用户名查找用户
     * 【Spring Boot/Data JPA】方法名即查询：findBy + 字段名
     * 自动生成 SQL: SELECT * FROM app_user WHERE username = ?
     *
     * @param username 用户名
     * @return Optional<User>，可能为空（用户不存在时）
     */
    Optional<User> findByUsername(String username);

    /**
     * 检查用户名是否已存在
     * 【Spring Boot/Data JPA】方法名即查询：existsBy + 字段名
     * 自动生成 SQL: SELECT COUNT(*) > 0 FROM app_user WHERE username = ?
     *
     * @param username 用户名
     * @return true=已存在，false=不存在
     */
    boolean existsByUsername(String username);
}
