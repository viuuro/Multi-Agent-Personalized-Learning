package com.edu.agent.repository;

import com.edu.agent.model.AgentDecisionRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentDecisionRecordRepository extends JpaRepository<AgentDecisionRecord, Long> {
    List<AgentDecisionRecord> findByUserIdAndConversationIdOrderByCreatedAtDesc(
            Long userId, String conversationId, Pageable pageable);
    void deleteByUserId(Long userId);
    void deleteByUserIdAndConversationId(Long userId, String conversationId);
}
