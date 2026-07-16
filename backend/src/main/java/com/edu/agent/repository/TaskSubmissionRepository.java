package com.edu.agent.repository;

import com.edu.agent.model.TaskSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

/**
 * 任务提交记录数据访问层 —— 提供 task_submission 表的 CRUD 操作
 *
 * 继承 JpaRepository 自动获得 save(), findById(), findAll(), delete() 等方法。
 * 自定义查询方法由 Spring Data JPA 根据方法命名规则自动生成 SQL 实现。
 *
 * ========== 【Spring Boot/Data JPA】方法命名查询 ==========
 *   findByTaskIdOrderBySubmissionTimeDesc → SELECT * FROM task_submission WHERE task_id = ? ORDER BY submission_time DESC
 *   findByUserIdOrderBySubmissionTimeDesc → SELECT * FROM task_submission WHERE user_id = ? ORDER BY submission_time DESC
 *   findByUserId                       → SELECT * FROM task_submission WHERE user_id = ?
 *   findByIdAndUserId                  → SELECT * FROM task_submission WHERE id = ? AND user_id = ?
 *   deleteByUserId                     → DELETE FROM task_submission WHERE user_id = ?
 *
 * ========== 调用入口 ==========
 *   - SubmissionService.submit()              — save() 保存提交
 *   - SubmissionService.getSubmissionDetail()  — findByIdAndUserId() 校验权限
 *   - SubmissionService.getSubmissionsByTask() — findByTaskIdOrderBySubmissionTimeDesc() 查列表
 *   - AuthService.deleteUser()                — findByUserId() + deleteByUserId() 级联删除
 */
@Repository  // 【Spring Boot/Data JPA】标记为数据访问仓库，启动时自动扫描并生成代理实现
public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {

    /**
     * 查询指定任务的所有提交（按提交时间倒序，最新的在前）
     *
     * @param taskId 任务 ID
     * @return 该任务的所有提交记录，按时间倒序排列
     */
    List<TaskSubmission> findByTaskIdOrderBySubmissionTimeDesc(Long taskId);

    /**
     * 查询指定用户的所有提交（按提交时间倒序）
     *
     * @param userId 用户 ID
     * @return 该用户的所有提交记录，按时间倒序排列
     */
    List<TaskSubmission> findByUserIdOrderBySubmissionTimeDesc(Long userId);

    /**
     * 查询指定用户的所有提交（用于账号注销时的级联清理）
     *
     * @param userId 用户 ID
     * @return 该用户的所有提交记录
     */
    List<TaskSubmission> findByUserId(Long userId);

    /**
     * 查询指定用户对指定任务的提交
     *
     * @param userId 用户 ID
     * @param taskId 任务 ID
     * @return 该用户对该任务的所有提交记录，按时间倒序排列
     */
    List<TaskSubmission> findByUserIdAndTaskIdOrderBySubmissionTimeDesc(Long userId, Long taskId);

    List<TaskSubmission> findByUserIdAndConversationIdOrderBySubmissionTimeDesc(
            Long userId, String conversationId);

    List<TaskSubmission> findByStatus(String status);

    /**
     * 根据 ID 和用户 ID 查询提交记录（同时校验存在和用户所有权）
     *
     * @param id     提交记录 ID
     * @param userId 用户 ID
     * @return Optional<TaskSubmission>，不存在或不属于该用户时返回 empty
     */
    Optional<TaskSubmission> findByIdAndUserId(Long id, Long userId);

    /**
     * 删除指定用户的所有提交（用于账号注销时的级联清理）
     *
     * @param userId 用户 ID
     */
    void deleteByUserId(Long userId);

    /** 查询指定时间区间内的学习成果提交。 */
    List<TaskSubmission> findByUserIdAndSubmissionTimeBetween(
            Long userId, LocalDateTime start, LocalDateTime end);
}
