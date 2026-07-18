package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.KnowledgeDocument;
import com.edu.agent.security.CurrentUser;
import com.edu.agent.service.KnowledgeBaseService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/** 知识库查询与文档管理接口；文件入库复用现有 /api/parse-file。 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping("/documents")
    public ApiResponse<List<KnowledgeDocument>> listDocuments(
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "100") int limit,
            Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        return ApiResponse.success(knowledgeBaseService.listAccessible(
                userId, conversationId, limit));
    }

    @GetMapping("/search")
    public ApiResponse<List<KnowledgeBaseService.SearchResult>> search(
            @RequestParam String q,
            @RequestParam(required = false) String conversationId,
            @RequestParam(defaultValue = "6") int limit,
            Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        return ApiResponse.success(knowledgeBaseService.search(userId, conversationId, q, limit));
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long documentId,
                                            Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        if (!knowledgeBaseService.deleteOwnedDocument(userId, documentId)) {
            throw new ResponseStatusException(NOT_FOUND, "知识文档不存在或不可删除");
        }
        return ApiResponse.success(null);
    }
}
