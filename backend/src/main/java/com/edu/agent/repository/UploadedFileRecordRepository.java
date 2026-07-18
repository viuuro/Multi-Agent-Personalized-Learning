package com.edu.agent.repository;

import com.edu.agent.model.UploadedFileRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UploadedFileRecordRepository extends JpaRepository<UploadedFileRecord, Long> {
    List<UploadedFileRecord> findByUserIdOrderByUploadedAtDesc(Long userId, Pageable pageable);
    void deleteByUserId(Long userId);
    void deleteByUserIdAndConversationId(Long userId, String conversationId);
}
