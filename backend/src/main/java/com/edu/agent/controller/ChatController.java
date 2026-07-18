package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.ChatMessage;
import com.edu.agent.model.Conversation;
import com.edu.agent.repository.ConversationRepository;
import com.edu.agent.service.ChatService;
import com.edu.agent.service.ConversationSessionService;
import com.edu.agent.service.ConversationDeletionService;
import com.edu.agent.service.RequestRateLimiter;
import com.edu.agent.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 聊天控制器 —— 提供 SSE 流式对话接口
 *
 * 核心接口：
 *   POST /api/chat — SSE 流式聊天（逐字打字效果 + 画像实时更新）
 *   GET  /api/health — 健康检查
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @RestController: 声明为 REST 控制器，Spring 自动将返回值序列化为 JSON/SSE
 *   - @RequestMapping("/api"): 为所有接口添加 /api 前缀
 *   - @PostMapping / @GetMapping: 映射 HTTP 方法和路径
 *   - SseEmitter: Spring MVC 内置的 SSE（Server-Sent Events）支持，用于流式推送
 *   - MediaType.TEXT_EVENT_STREAM_VALUE: 声明响应内容类型为 SSE 事件流
 *   - 构造器注入: ChatService 由 Spring 自动装配
 */
@RestController  // 【Spring Boot】声明为 REST 控制器，自动处理 JSON 序列化
@RequestMapping("/api")  // 【Spring Boot】为类中所有方法添加 /api 路径前缀
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ConversationRepository conversationRepository;
    private final ConversationSessionService conversationSessionService;
    private final ConversationDeletionService conversationDeletionService;
    private final RequestRateLimiter rateLimiter;

    /** 【Spring Boot】构造器注入 */
    public ChatController(ChatService chatService,
                          ConversationRepository conversationRepository,
                          ConversationSessionService conversationSessionService,
                          ConversationDeletionService conversationDeletionService,
                          RequestRateLimiter rateLimiter) {
        this.chatService = chatService;
        this.conversationRepository = conversationRepository;
        this.conversationSessionService = conversationSessionService;
        this.conversationDeletionService = conversationDeletionService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * POST /api/chat —— SSE 流式聊天接口
     *
     * 输入：JSON { message: "用户消息", conversationId?: "会话ID" }
     * 输出：SSE 事件流
     *   - event: conversation → 会话 ID
     *   - event: message      → AI 回复（逐字推送）
     *   - event: profile      → 画像更新数据
     *   - event: done         → 流式输出完成
     *
     * produces = TEXT_EVENT_STREAM_VALUE 声明响应为 SSE 格式
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)  // 【Spring Boot】POST 映射 + SSE 流式响应
    public SseEmitter chat(@RequestBody ChatMessage chatMessage, Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        if (chatMessage.getMessage() != null && chatMessage.getMessage().length() > 20_000) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "消息内容过长");
        }
        if (chatMessage.getImageData() != null && chatMessage.getImageData().length() > 8 * 1024 * 1024) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过约 6MB");
        }
        if (!rateLimiter.tryAcquire("chat:" + userId, 30, 60)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "对话请求过于频繁");
        }
        String imageData = chatMessage.getImageData();
        boolean hasImage = imageData != null && !imageData.isEmpty();
        int imageLen = hasImage ? imageData.length() : 0;
        log.info(">>> POST /api/chat —— message: {}, hasImage: {}, imageDataLength: {}",
                chatMessage.getMessage(), hasImage, imageLen);
        if (hasImage) {
            log.info(">>> imageData prefix: {}", imageData.substring(0, Math.min(50, imageLen)));
        }
        return chatService.handleChat(
                chatMessage.getMessage(),
                chatMessage.getDisplayMessage(),
                chatMessage.getConversationId(),
                imageData,
                chatMessage.getAttachmentName(),
                chatMessage.getAttachmentType(),
                userId);
    }

    /**
     * GET /api/health —— 健康检查接口
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of(
                "status", "ok",
                "service", "edu-agent"
        ));
    }

    /**
     * GET /api/conversations —— 获取用户最近的对话历史
     * 登录后加载历史消息，恢复聊天上下文
     */
    @GetMapping("/conversations")
    public ApiResponse<List<Conversation>> getConversations(@RequestParam(required = false) Long userId,
                                                            Authentication authentication,
                                                            @RequestParam(defaultValue = "50") int limit) {
        userId = CurrentUser.id(authentication);
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        List<Conversation> messages = conversationRepository.findLatestByUserId(userId, safeLimit);
        // 数据库按倒序查的，反转为时间正序给前端
        Collections.reverse(messages);
        Map<String, String> titles = conversationSessionService.getTitleMap(userId);
        messages.forEach(message -> message.setConversationTitle(
                titles.get(message.getConversationId())));
        return ApiResponse.success("ok", messages);
    }

    /** DELETE /api/conversations/{conversationId} — 删除当前用户的一整个对话工作区。 */
    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Map<String, String>> deleteConversation(
            @PathVariable String conversationId, Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        if (!conversationDeletionService.delete(userId, conversationId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "对话不存在");
        }
        return ApiResponse.success("对话已删除", Map.of("conversationId", conversationId));
    }

    /** POST /api/conversations/title —— 基于当前会话的整体内容生成简短标题。 */
    @PostMapping("/conversations/title")
    public ApiResponse<Map<String, String>> generateConversationTitle(
            @RequestBody Map<String, Object> body, Authentication authentication) {
        String conversationId = String.valueOf(body.getOrDefault("conversationId", "")).trim();
        if (conversationId.isBlank()) {
            return ApiResponse.error(400, "缺少 conversationId");
        }
        Long userId = CurrentUser.id(authentication);
        String context = String.valueOf(body.getOrDefault("conversationContext", ""));
        String title = chatService.generateConversationTitle(context);
        title = conversationSessionService.saveTitle(userId, conversationId, title);
        return ApiResponse.success("ok", Map.of("title", title));
    }
}
