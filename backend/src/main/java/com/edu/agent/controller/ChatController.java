package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.ChatMessage;
import com.edu.agent.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    private final ChatService chatService;  // 【Spring Boot】构造器注入的业务服务

    /** 【Spring Boot】构造器注入 —— Spring 自动解析并注入 ChatService Bean */
    public ChatController(ChatService chatService) {
        this.chatService = chatService;
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
    public SseEmitter chat(@RequestBody ChatMessage chatMessage) {  // 【Spring Boot】@RequestBody: JSON → Java 对象；SseEmitter: SSE 长连接
        String imageData = chatMessage.getImageData();
        boolean hasImage = imageData != null && !imageData.isEmpty();
        int imageLen = hasImage ? imageData.length() : 0;
        log.info(">>> POST /api/chat —— message: {}, hasImage: {}, imageDataLength: {}",
                chatMessage.getMessage(), hasImage, imageLen);
        if (hasImage) {
            log.info(">>> imageData prefix: {}", imageData.substring(0, Math.min(50, imageLen)));
        }
        return chatService.handleChat(chatMessage.getMessage(), chatMessage.getConversationId(), imageData, chatMessage.getUserId());
    }

    /**
     * GET /api/health —— 健康检查接口
     * 用于验证服务是否正常运行
     */
    @GetMapping("/health")  // 【Spring Boot】GET 映射
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of(
                "status", "ok",
                "service", "edu-agent"
        ));
    }
}
