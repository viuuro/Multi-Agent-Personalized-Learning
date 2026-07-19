package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.security.CurrentUser;
import com.edu.agent.service.KnowledgeBaseService;
import com.edu.agent.service.RequestRateLimiter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

/** 聊天框多模态资源生成入口；Spring 负责鉴权和知识库上下文，Python 负责智能体与渲染。 */
@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final RequestRateLimiter rateLimiter;
    private final String pythonAiUrl;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();

    public ArtifactController(ObjectMapper objectMapper,
                              KnowledgeBaseService knowledgeBaseService,
                              RequestRateLimiter rateLimiter,
                              @Value("${python.ai.url:http://localhost:8000}") String pythonAiUrl) {
        this.objectMapper = objectMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.rateLimiter = rateLimiter;
        this.pythonAiUrl = pythonAiUrl;
    }

    @PostMapping("/image")
    public ApiResponse<Map<String, Object>> generateImage(@RequestBody Map<String, Object> body,
                                                          Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        String prompt = prompt(body);
        rateLimit(userId);
        HttpResponse<String> response = sendText("/artifacts/image", payload(userId, prompt, body));
        if (response.statusCode() != 200) {
            throw new ResponseStatusException(BAD_GATEWAY, "图片生成服务暂时不可用");
        }
        try {
            Map<String, Object> result = objectMapper.readValue(
                    response.body(), new TypeReference<Map<String, Object>>() {});
            return ApiResponse.success("图片资源已生成", result);
        } catch (Exception exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "图片生成结果无法解析", exception);
        }
    }

    private Map<String, Object> payload(Long userId, String prompt, Map<String, Object> body) {
        String conversationId = text(body.get("conversationId"), 64);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prompt", prompt);
        payload.put("conversation_id", conversationId);
        payload.put("knowledge_context", knowledgeBaseService.buildContext(userId, conversationId, prompt, 6));
        return payload;
    }

    private HttpResponse<String> sendText(String path, Map<String, Object> payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(pythonAiUrl + path))
                    .timeout(Duration.ofSeconds(150)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload))).build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "资源生成请求已中断", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "无法连接资源生成服务", exception);
        }
    }

    private String prompt(Map<String, Object> body) {
        String prompt = text(body.get("prompt"), 5000);
        if (prompt.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "生成要求不能为空");
        return prompt;
    }

    private void rateLimit(Long userId) {
        if (!rateLimiter.tryAcquire("artifact:" + userId, 8, 60)) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, "资源生成过于频繁，请稍后再试");
        }
    }

    private String text(Object value, int limit) {
        String result = value == null ? "" : String.valueOf(value).trim();
        return result.substring(0, Math.min(result.length(), limit));
    }

}
