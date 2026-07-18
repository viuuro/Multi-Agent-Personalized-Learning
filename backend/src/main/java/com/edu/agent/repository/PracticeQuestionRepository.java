package com.edu.agent.repository;

import com.edu.agent.model.PracticeQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PracticeQuestionRepository extends JpaRepository<PracticeQuestion, Long> {
    List<PracticeQuestion> findByUserIdAndConversationIdOrderByCreatedAtDescIdAsc(
            Long userId, String conversationId);
    Optional<PracticeQuestion> findByIdAndUserId(Long id, Long userId);
    void deleteByUserId(Long userId);
    void deleteByUserIdAndConversationId(Long userId, String conversationId);
}
