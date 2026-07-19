package com.edu.agent.repository;

import com.edu.agent.model.LearningResourceFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningResourceFeedbackRepository extends JpaRepository<LearningResourceFeedback, Long> {
    Optional<LearningResourceFeedback> findByUserIdAndResourceKey(Long userId, String resourceKey);
    List<LearningResourceFeedback> findTop100ByUserIdOrderByUpdatedAtDesc(Long userId);
    void deleteByUserId(Long userId);
    void deleteByUserIdAndConversationId(Long userId, String conversationId);
}
