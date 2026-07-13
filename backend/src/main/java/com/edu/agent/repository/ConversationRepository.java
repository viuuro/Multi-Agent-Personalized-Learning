package com.edu.agent.repository;

import com.edu.agent.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 对话记录数据访问层
 *
 * 提供对话记录的 CRUD 操作。
 * 通过 user_id 字段实现多用户数据隔离。
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * 删除指定用户的所有对话记录
     */
    void deleteByUserId(Long userId);

    /**
     * 查询指定用户最近的对话记录（按时间倒序，取前 N 条）
     */
    @Query("SELECT c FROM Conversation c WHERE c.userId = :userId ORDER BY c.timestamp DESC")
    List<Conversation> findRecentByUserId(@Param("userId") Long userId);

    /**
     * 查询指定用户最近 N 条对话记录（按时间正序，用于前端展示）
     */
    @Query(value = "SELECT * FROM conversation WHERE user_id = :userId ORDER BY timestamp DESC LIMIT :limit", nativeQuery = true)
    List<Conversation> findLatestByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    /** 查询指定对话的完整消息，按时间倒序。 */
    List<Conversation> findByUserIdAndConversationIdOrderByTimestampDesc(
            Long userId, String conversationId);

    /** 统计时间区间内的用户对话消息（不包含智能体回复）。 */
    List<Conversation> findByUserIdAndRoleAndTimestampBetween(
            Long userId, String role, LocalDateTime start, LocalDateTime end);
}
