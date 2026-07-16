package com.edu.agent.repository;

import com.edu.agent.model.ConversationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationSessionRepository extends JpaRepository<ConversationSession, Long> {
    Optional<ConversationSession> findByUserIdAndConversationId(Long userId, String conversationId);
    List<ConversationSession> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
