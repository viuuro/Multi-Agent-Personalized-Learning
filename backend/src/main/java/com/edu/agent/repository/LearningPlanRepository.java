package com.edu.agent.repository;

import com.edu.agent.model.LearningPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

/**
 * 学习计划数据访问层
 */
@Repository
public interface LearningPlanRepository extends JpaRepository<LearningPlanEntity, Long> {

    /**
     * 查询指定用户最近一次的学习计划
     */
    Optional<LearningPlanEntity> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);

    /** 查询指定对话最近一次保存的学习计划。 */
    Optional<LearningPlanEntity> findFirstByUserIdAndConversationIdOrderByUpdatedAtDesc(
            Long userId, String conversationId);

    List<LearningPlanEntity> findByUserIdAndConversationIdOrderByVersionNumberDesc(
            Long userId, String conversationId);

    /**
     * 删除指定用户的所有学习计划
     */
    void deleteByUserId(Long userId);
}
