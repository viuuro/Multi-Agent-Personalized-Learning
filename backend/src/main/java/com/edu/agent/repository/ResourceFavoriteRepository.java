package com.edu.agent.repository;

import com.edu.agent.model.ResourceFavorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResourceFavoriteRepository extends JpaRepository<ResourceFavorite, Long> {
    List<ResourceFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<ResourceFavorite> findByIdAndUserId(Long id, Long userId);
    Optional<ResourceFavorite> findByCollectionIdAndResourceKey(Long collectionId, String resourceKey);
    void deleteByCollectionId(Long collectionId);
    void deleteByUserId(Long userId);
}
