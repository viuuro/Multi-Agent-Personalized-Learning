package com.edu.agent.repository;

import com.edu.agent.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 任务数据访问层 —— 提供 learning_task 表的 CRUD 操作
 *
 * 继承 JpaRepository 自动获得 save(), findById(), findAll(), delete() 等方法。
 * 自定义查询方法由 Spring Data JPA 根据方法命名规则自动生成 SQL 实现。
 *
 * ========== 【Spring Boot/Data JPA】方法命名查询 ==========
 *   findByUserId         → SELECT * FROM learning_task WHERE user_id = ?
 *   findByPlanId         → SELECT * FROM learning_task WHERE plan_id = ?
 *   findByIdAndUserId    → SELECT * FROM learning_task WHERE id = ? AND user_id = ?
 *   deleteByUserId       → DELETE FROM learning_task WHERE user_id = ?
 *
 * ========== 调用入口 ==========
 *   - SubmissionService.submit()        — 通过 findByIdAndUserId() 校验任务存在和权限
 *   - SubmissionService.getSubmissionsByTask() — 通过 findByIdAndUserId() 校验权限
 *   - AuthService.deleteUser()          — 通过 deleteByUserId() 级联删除
 */
@Repository  // 【Spring Boot/Data JPA】标记为数据访问仓库，启动时自动扫描并生成代理实现
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * 查询指定用户的所有任务
     *
     * @param userId 用户 ID
     * @return 该用户的所有任务列表
     */
    List<Task> findByUserId(Long userId);

    /**
     * 查询指定学习计划下的所有任务
     *
     * @param planId 学习计划 ID
     * @return 该计划下的所有任务列表
     */
    List<Task> findByPlanId(Long planId);

    /**
     * 查询指定用户在指定计划下的所有任务
     *
     * @param userId 用户 ID
     * @param planId 学习计划 ID
     * @return 该用户在该计划下的任务列表
     */
    List<Task> findByUserIdAndPlanId(Long userId, Long planId);

    /**
     * 根据 ID 和用户 ID 查询任务（同时校验任务存在和用户所有权）
     *
     * 在 SubmissionService 中用于权限校验：确保用户只能提交属于自己的任务。
     *
     * @param id     任务 ID
     * @param userId 用户 ID
     * @return Optional<Task>，任务不存在或不属于该用户时返回 empty
     */
    Optional<Task> findByIdAndUserId(Long id, Long userId);

    /**
     * 删除指定用户的所有任务（用于账号注销时的级联清理）
     *
     * @param userId 用户 ID
     */
    void deleteByUserId(Long userId);
}