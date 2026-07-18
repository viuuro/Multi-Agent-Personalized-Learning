package com.edu.agent.repository;

import com.edu.agent.model.KnowledgeDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    @Query("""
            SELECT d FROM KnowledgeDocument d
            WHERE d.scope = :scope AND d.checksum = :checksum
              AND ((:userId IS NULL AND d.userId IS NULL) OR d.userId = :userId)
              AND ((:conversationId IS NULL AND d.conversationId IS NULL)
                   OR d.conversationId = :conversationId)
            ORDER BY d.id DESC
            """)
    List<KnowledgeDocument> findDuplicates(@Param("userId") Long userId,
                                           @Param("conversationId") String conversationId,
                                           @Param("scope") String scope,
                                           @Param("checksum") String checksum,
                                           Pageable pageable);

    @Query("""
            SELECT d FROM KnowledgeDocument d
            WHERE d.scope = 'GLOBAL'
               OR (d.userId = :userId
                   AND (d.scope <> 'CONVERSATION' OR d.conversationId = :conversationId))
            ORDER BY d.updatedAt DESC
            """)
    List<KnowledgeDocument> findAccessible(@Param("userId") Long userId,
                                           @Param("conversationId") String conversationId,
                                           Pageable pageable);

    Optional<KnowledgeDocument> findByIdAndUserId(Long id, Long userId);
    List<KnowledgeDocument> findByScopeAndSourceTypeOrderByIdDesc(String scope, String sourceType);
    void deleteByUserId(Long userId);
    void deleteByUserIdAndConversationId(Long userId, String conversationId);
}
