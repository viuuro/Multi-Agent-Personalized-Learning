package com.edu.agent.repository;

import com.edu.agent.model.KnowledgeChunk;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    @Query("""
            SELECT k FROM KnowledgeChunk k
            WHERE k.scope = 'GLOBAL'
               OR (k.userId = :userId
                   AND (k.scope <> 'CONVERSATION' OR k.conversationId = :conversationId))
            ORDER BY k.id DESC
            """)
    List<KnowledgeChunk> findAccessible(@Param("userId") Long userId,
                                        @Param("conversationId") String conversationId,
                                        Pageable pageable);

    void deleteByDocumentId(Long documentId);
    void deleteByUserId(Long userId);
    void deleteByUserIdAndConversationId(Long userId, String conversationId);
}
