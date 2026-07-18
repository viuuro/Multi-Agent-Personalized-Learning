package com.edu.agent.repository;

import com.edu.agent.model.ProfileEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileEvidenceRepository extends JpaRepository<ProfileEvidence, Long> {
    List<ProfileEvidence> findByUserIdAndConversationIdOrderByCreatedAtDesc(Long userId, String conversationId);
    List<ProfileEvidence> findByUserIdAndConversationIdAndDimensionAndStatusOrderByCreatedAtDesc(
            Long userId, String conversationId, String dimension, String status);
    void deleteByUserId(Long userId);
    void deleteByUserIdAndConversationId(Long userId, String conversationId);
}
