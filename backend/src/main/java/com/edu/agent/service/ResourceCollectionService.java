package com.edu.agent.service;

import com.edu.agent.model.ResourceCollection;
import com.edu.agent.model.ResourceFavorite;
import com.edu.agent.repository.ResourceCollectionRepository;
import com.edu.agent.repository.ResourceFavoriteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class ResourceCollectionService {
    private static final int MAX_COLLECTIONS = 24;
    private static final int MAX_FAVORITES = 500;
    private final ResourceCollectionRepository collectionRepository;
    private final ResourceFavoriteRepository favoriteRepository;

    public ResourceCollectionService(ResourceCollectionRepository collectionRepository,
                                     ResourceFavoriteRepository favoriteRepository) {
        this.collectionRepository = collectionRepository;
        this.favoriteRepository = favoriteRepository;
    }

    @Transactional
    public List<CollectionView> list(Long userId) {
        ensureCourseCollections(userId);
        List<ResourceFavorite> favorites = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, List<FavoriteView>> grouped = new HashMap<>();
        for (ResourceFavorite favorite : favorites) {
            grouped.computeIfAbsent(favorite.getCollectionId(), ignored -> new ArrayList<>()).add(view(favorite));
        }
        return collectionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(collection -> new CollectionView(collection.getId(), collection.getName(), collection.getKind(),
                        collection.getCourseKey(), grouped.getOrDefault(collection.getId(), List.of())))
                .toList();
    }

    @Transactional
    public CollectionView create(Long userId, String name) {
        String safeName = text(name, 80);
        if (safeName.isBlank()) throw new IllegalArgumentException("收藏夹名称不能为空");
        if (collectionRepository.findByUserIdOrderByUpdatedAtDesc(userId).size() >= MAX_COLLECTIONS) {
            throw new IllegalArgumentException("收藏夹数量已达上限");
        }
        if (collectionRepository.findByUserIdAndName(userId, safeName).isPresent()) {
            throw new IllegalArgumentException("同名收藏夹已存在");
        }
        ResourceCollection collection = new ResourceCollection();
        collection.setUserId(userId);
        collection.setName(safeName);
        collection.setKind(ResourceCollection.KIND_CUSTOM);
        collection = collectionRepository.save(collection);
        return new CollectionView(collection.getId(), collection.getName(), collection.getKind(), null, List.of());
    }

    @Transactional
    public FavoriteView add(Long userId, Long collectionId, Map<String, Object> body) {
        ResourceCollection collection = collectionRepository.findByIdAndUserId(collectionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("收藏夹不存在或无权访问"));
        String url = text(body.get("url"), 2000);
        if (!url.startsWith("https://")) throw new IllegalArgumentException("资源链接必须使用 HTTPS");
        if (favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).size() >= MAX_FAVORITES) {
            throw new IllegalArgumentException("收藏资源数量已达上限");
        }
        String key = sha256(url);
        ResourceFavorite favorite = favoriteRepository.findByCollectionIdAndResourceKey(collectionId, key)
                .orElseGet(ResourceFavorite::new);
        favorite.setUserId(userId);
        favorite.setCollectionId(collection.getId());
        favorite.setConversationId(nullableText(body.get("conversationId"), 64));
        favorite.setResourceKey(key);
        favorite.setTitle(requiredText(body.get("title"), 500, "资源标题不能为空"));
        favorite.setUrl(url);
        favorite.setPlatform(nullableText(body.get("platform"), 120));
        favorite.setResourceType(nullableText(body.get("type"), 40));
        favorite.setCourseKey(nullableText(body.get("courseKey"), 64));
        favorite.setChapterKey(nullableText(body.get("chapterKey"), 64));
        return view(favoriteRepository.save(favorite));
    }

    @Transactional
    public void removeFavorite(Long userId, Long favoriteId) {
        ResourceFavorite favorite = favoriteRepository.findByIdAndUserId(favoriteId, userId)
                .orElseThrow(() -> new IllegalArgumentException("收藏资源不存在或无权访问"));
        favoriteRepository.delete(favorite);
    }

    @Transactional
    public void removeCollection(Long userId, Long collectionId) {
        ResourceCollection collection = collectionRepository.findByIdAndUserId(collectionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("收藏夹不存在或无权访问"));
        if (ResourceCollection.KIND_COURSE.equals(collection.getKind())) {
            throw new IllegalArgumentException("课程收藏夹不可删除");
        }
        favoriteRepository.deleteByCollectionId(collectionId);
        collectionRepository.delete(collection);
    }

    private void ensureCourseCollections(Long userId) {
        ensureCourseCollection(userId, "数据结构", "data-structures");
        ensureCourseCollection(userId, "计算机组成原理", "computer-organization");
    }

    private void ensureCourseCollection(Long userId, String name, String key) {
        if (collectionRepository.findByUserIdAndName(userId, name).isPresent()) return;
        ResourceCollection collection = new ResourceCollection();
        collection.setUserId(userId);
        collection.setName(name);
        collection.setKind(ResourceCollection.KIND_COURSE);
        collection.setCourseKey(key);
        collectionRepository.save(collection);
    }

    private FavoriteView view(ResourceFavorite favorite) {
        return new FavoriteView(favorite.getId(), favorite.getCollectionId(), favorite.getTitle(), favorite.getUrl(),
                favorite.getPlatform(), favorite.getResourceType(), favorite.getCourseKey(), favorite.getChapterKey(),
                favorite.getCreatedAt());
    }

    private String requiredText(Object value, int max, String message) {
        String result = text(value, max);
        if (result.isBlank()) throw new IllegalArgumentException(message);
        return result;
    }

    private String nullableText(Object value, int max) {
        String result = text(value, max);
        return result.isBlank() ? null : result;
    }

    private String text(Object value, int max) {
        String result = value == null ? "" : String.valueOf(value).trim();
        return result.substring(0, Math.min(result.length(), max));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算资源标识", exception);
        }
    }

    public record CollectionView(Long id, String name, String kind, String courseKey, List<FavoriteView> resources) {}
    public record FavoriteView(Long id, Long collectionId, String title, String url, String platform,
                               String type, String courseKey, String chapterKey, java.time.LocalDateTime createdAt) {}
}
