package com.edu.agent.repository;

import com.edu.agent.model.ResourceCollection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceCollectionRepository extends JpaRepository<ResourceCollection, Long> {
    List<ResourceCollection> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<ResourceCollection> findByIdAndUserId(Long id, Long userId);
    Optional<ResourceCollection> findByUserIdAndName(Long userId, String name);
    void deleteByUserId(Long userId);
}
