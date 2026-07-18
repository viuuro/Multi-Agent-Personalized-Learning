package com.edu.agent.repository;

import com.edu.agent.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户画像数据访问层
 *
 * 每个用户拥有独立的画像记录，通过 user_id 字段隔离。
 * 每次更新画像会新增一条记录（保留历史版本）。
 * ProfileService 始终取指定用户的最新记录作为当前画像。
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - extends JpaRepository<UserProfile, Long>: 自动获得 find/save/delete 等方法
 *   - 方法命名查询: Spring Data JPA 根据方法名自动生成 SQL
 */
@Repository  // 【Spring Boot/Data JPA】数据访问仓库
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    /**
     * 按用户 ID 查询画像列表，按更新时间降序排列
     * 自动生成 SQL: SELECT * FROM user_profile WHERE user_id = ? ORDER BY updated_at DESC
     */
    List<UserProfile> findByUserIdOrderByUpdatedAtDesc(Long userId);

    /**
     * 按用户 ID 查询最新一条画像
     * 自动生成 SQL: SELECT * FROM user_profile WHERE user_id = ? ORDER BY updated_at DESC LIMIT 1
     */
    Optional<UserProfile> findFirstByUserIdOrderByUpdatedAtDesc(Long userId);

    /** 查询指定用户在指定对话中的最新画像。 */
    Optional<UserProfile> findFirstByUserIdAndConversationIdOrderByUpdatedAtDescIdDesc(
            Long userId, String conversationId);

    /**
     * 删除指定用户的所有画像记录
     * 自动生成 SQL: DELETE FROM user_profile WHERE user_id = ?
     */
    void deleteByUserId(Long userId);

    void deleteByUserIdAndConversationId(Long userId, String conversationId);
}
