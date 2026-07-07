package com.edu.agent.repository;

import com.edu.agent.model.AiEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AI 评价结果数据访问层 —— 提供 ai_evaluation 表的 CRUD 操作
 *
 * 继承 JpaRepository 自动获得 save(), findById(), findAll(), delete() 等方法。
 * 自定义查询方法由 Spring Data JPA 根据方法命名规则自动生成 SQL 实现。
 *
 * ========== 【Spring Boot/Data JPA】方法命名查询 ==========
 *   findBySubmissionId   → SELECT * FROM ai_evaluation WHERE submission_id = ?
 *   deleteBySubmissionId → DELETE FROM ai_evaluation WHERE submission_id = ?
 *
 * ========== 调用入口 ==========
 *   - SubmissionService.getSubmissionDetail()  — findBySubmissionId() 查询与提交关联的评价
 *   - SubmissionService.getSubmissionsByTask() — 遍历提交列表时逐个查询评价
 *   - AuthService.deleteUser()                — deleteBySubmissionId() 在删除提交前先删除关联评价
 *
 * ========== 与 TaskSubmission 的关系 ==========
 *   task_submission 与 ai_evaluation 是 1:1 关系（一个提交最多对应一条评价记录）。
 *   通过 submission_id 字段关联，无外键约束。
 */
@Repository  // 【Spring Boot/Data JPA】标记为数据访问仓库，启动时自动扫描并生成代理实现
public interface AiEvaluationRepository extends JpaRepository<AiEvaluation, Long> {

    /**
     * 根据提交记录 ID 查询 AI 评价结果
     *
     * 由于 task_submission 与 ai_evaluation 是 1:1 关系，
     * 每个 submission_id 最多对应一条评价记录。
     *
     * @param submissionId 提交记录 ID
     * @return Optional<AiEvaluation>，评价未完成或不存在时返回 empty
     */
    Optional<AiEvaluation> findBySubmissionId(Long submissionId);

    /**
     * 删除指定提交记录的 AI 评价结果（用于账号注销时的级联清理）
     * 调用前需先通过 findBySubmissionId() 或 findByUserId() 获取关联的 submission_id
     *
     * @param submissionId 提交记录 ID
     */
    void deleteBySubmissionId(Long submissionId);
}