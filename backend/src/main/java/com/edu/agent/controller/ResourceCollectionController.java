package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.security.CurrentUser;
import com.edu.agent.service.ResourceCollectionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resource-collections")
public class ResourceCollectionController {
    private final ResourceCollectionService service;

    public ResourceCollectionController(ResourceCollectionService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<ResourceCollectionService.CollectionView>> list(Authentication authentication) {
        return ApiResponse.success("ok", service.list(CurrentUser.id(authentication)));
    }

    @PostMapping
    public ApiResponse<ResourceCollectionService.CollectionView> create(
            @RequestBody Map<String, Object> body, Authentication authentication) {
        return ApiResponse.success("收藏夹已创建",
                service.create(CurrentUser.id(authentication), String.valueOf(body.getOrDefault("name", ""))));
    }

    @PostMapping("/{collectionId}/resources")
    public ApiResponse<ResourceCollectionService.FavoriteView> addResource(
            @PathVariable Long collectionId, @RequestBody Map<String, Object> body, Authentication authentication) {
        return ApiResponse.success("资源已收藏", service.add(CurrentUser.id(authentication), collectionId, body));
    }

    @DeleteMapping("/{collectionId}/resources/{favoriteId}")
    public ApiResponse<Void> removeResource(@PathVariable Long collectionId, @PathVariable Long favoriteId,
                                            Authentication authentication) {
        service.removeFavorite(CurrentUser.id(authentication), favoriteId);
        return ApiResponse.success("已取消收藏", null);
    }

    @DeleteMapping("/{collectionId}")
    public ApiResponse<Void> removeCollection(@PathVariable Long collectionId, Authentication authentication) {
        service.removeCollection(CurrentUser.id(authentication), collectionId);
        return ApiResponse.success("收藏夹已删除", null);
    }
}
