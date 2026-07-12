package com.edu.agent.service;

import com.edu.agent.model.Conversation;
import com.edu.agent.model.UserProfile;
import com.edu.agent.repository.ConversationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 聊天服务 —— 处理对话消息并实现 SSE 流式输出
 *
 * 核心职责：
 *   1. 接收用户消息，调用 Python AI 服务提取画像并生成回复
 *   2. 通过 SSE（Server-Sent Events）逐字符流式推送给前端
 *   3. 保存对话记录到 MySQL
 *   4. 更新用户画像到 MySQL
 *
 * 工作流程：
 *   前端发送消息 → ChatController → ChatService.handleChat()
 *     → 调用 Python AI /chat（提取画像 + 生成回复）
 *     → 保存用户消息到 MySQL
 *     → 更新用户画像到 MySQL
 *     → SSE 逐字符推送 AI 回复（实现打字机效果）
 *     → 推送画像更新通知
 *     → 推送完成信号
 *     → 保存 AI 回复到 MySQL
 *
 * 降级策略：
 *   - 真实模式：调用 Python AI 服务（FastAPI + LangChain → MiMo-v2.5 API）
 *   - Mock 模式：使用 MockAiService 提供预设数据（无 API Key 时自动降级）
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Service: 声明为业务服务层 Bean
 *   - @Value: 从 application.yml 注入 Python AI 服务地址
 *   - SseEmitter: Spring MVC 内置的 SSE 支持，用于流式推送
 *   - 构造器注入: ConversationRepository, ProfileService, MockAiService
 */
@Service  // 【Spring Boot】声明为业务服务 Bean
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** 【Spring Boot/Data JPA】对话记录仓库，用于保存聊天消息到 MySQL */
    private final ConversationRepository conversationRepository;
    /** 用户画像服务，用于读取和更新画像 */
    private final ProfileService profileService;
    /** Mock AI 服务，用于无 API Key 时的降级模式 */
    private final MockAiService mockAiService;
    /** JSON 序列化/反序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** Java 11+ 内置的 HTTP 客户端，用于调用 Python AI 服务 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))  // 连接超时 10 秒
            .build();

    /** Python AI 服务地址，从 application.yml 的 python.ai.url 配置读取 */
    @Value("${python.ai.url:http://localhost:8000}")  // 【Spring Boot】属性注入
    private String pythonAiUrl;

    /** 是否使用 Mock 模式（无 MIMO_API_KEY 环境变量时自动启用） */
    private final boolean mockMode;

    /** 【Spring Boot】构造器注入 —— Spring 自动装配所有依赖 */
    public ChatService(ConversationRepository conversationRepository,
                       ProfileService profileService,
                       MockAiService mockAiService,
                       @Value("${ai.mock-enabled:false}") boolean mockMode) {
        this.conversationRepository = conversationRepository;
        this.profileService = profileService;
        this.mockAiService = mockAiService;
        // MiMo API Key 只需要配置在 Python AI 服务中；Spring 默认始终调用 Python。
        // 只有显式设置 AI_MOCK_ENABLED=true 时才启用本地 Mock 模式。
        this.mockMode = mockMode;
        log.info(">>> ChatService 初始化完成，模式: {}", mockMode ? "MOCK" : "REAL (Python AI → MiMo-v2.5)");
    }

    /**
     * 处理聊天消息 —— 核心入口方法
     *
     * 完整流程：
     *   1. 生成/确认会话 ID（conversationId）
     *   2. 保存用户消息到 MySQL
     *   3. 调用 Python AI 提取画像 + 生成回复（或使用 Mock）
     *   4. 更新用户画像到 MySQL
     *   5. 创建 SSE 长连接，逐字符推送 AI 回复
     *   6. 推送画像更新通知
     *   7. 保存 AI 回复到 MySQL
     *
     * @param userMessage    用户输入的消息文本
     * @param conversationId 会话 ID（可选，首次对话为空时自动生成）
     * @return SseEmitter SSE 长连接对象，前端通过此连接接收流式数据
     */
    public SseEmitter handleChat(String userMessage, String conversationId, String imageData, Long userId) {
        // Step 1: 生成会话 ID（如果前端未提供）
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        final String convId = conversationId;
        // 创建 SSE 发射器（0L = 不设置超时，由后端控制完成时机）
        SseEmitter emitter = new SseEmitter(0L);

        // Step 2: 保存用户消息到 MySQL
        saveMessage(userMessage, "user", convId, userId);

        // Step 3: 提取画像 + 生成回复
        UserProfile extractedProfile;
        String fullResponse;
        if (mockMode) {
            // Mock 模式：使用预设数据
            extractedProfile = mockAiService.mockProfileExtraction(userMessage);
            fullResponse = mockAiService.mockChatResponse(userMessage, extractedProfile);
        } else {
            // 真实模式：调用 Python AI 服务（FastAPI + LangChain → MiMo-v2.5 API）
            Map<String, String> result = callPythonChat(userMessage, imageData, userId);
            fullResponse = result.get("response");
            String profileJson = result.get("profile_json");
            // 将 Python 返回的 JSON 解析为 UserProfile 对象
            extractedProfile = profileService.parseProfileJson(profileJson);
        }

        // Step 4: 更新用户画像到 MySQL
        UserProfile savedProfile = profileService.updateProfile(extractedProfile, userId);

        // Step 5-7: 在新线程中执行 SSE 流式推送（避免阻塞主线程）
        final String finalResponse = fullResponse;
        new Thread(() -> {
            try {
                // Step 5a: 推送会话 ID（前端用于关联后续消息）
                sendSSE(emitter, "conversation_id", convId);
                // Step 5b: 逐字符推送 AI 回复（实现打字机效果）
                // 使用 codePointAt() 正确处理 Unicode 字符（包括 emoji）
                int len = finalResponse.length();
                for (int i = 0; i < len; ) {
                    int cp = finalResponse.codePointAt(i);  // 获取 Unicode 码点
                    sendSSE(emitter, "content", new String(Character.toChars(cp)));
                    i += Character.charCount(cp);  // 跳过当前字符（可能是 1 或 2 个 char）
                    Thread.sleep(15);  // 每个字符间隔 15ms，模拟打字速度
                }

                // Step 6: 推送画像更新通知（前端用于更新雷达图）
                String profileJson = savedProfile.getProfileJson();
                sendSSE(emitter, "profile_update", profileJson);
                // Step 7a: 推送完成信号（前端用于结束流式状态）
                sendSSE(emitter, "done", "");

                // Step 7b: 保存 AI 回复到 MySQL
                saveMessage(finalResponse, "assistant", convId, userId);
                // 关闭 SSE 连接
                emitter.complete();
            } catch (IOException | InterruptedException e) {
                log.error("SSE 流输出异常: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    // ===== Python AI 调用 =====

    /**
     * 调用 Python AI 服务的 /chat 端点
     *
     * 发送用户消息和当前画像给 Python 服务，
     * Python 服务使用 LangChain 调用 MiMo-v2.5 API 提取画像并生成回复。
     *
     * @param userMessage 用户输入的消息
     * @return Map { "response": AI回复, "profile_json": 更新后的画像JSON }
     */
    private Map<String, String> callPythonChat(String userMessage, String imageData, Long userId) {
        try {
            // 获取当前画像 JSON 传给 Python（用于画像增量更新）
            UserProfile currentProfile = profileService.getCurrentProfile(userId);
            Map<String, Object> body;
            if (imageData != null && !imageData.isEmpty()) {
                // 有图片时，传递图片数据
                log.info(">>> 传递图片数据给 Python AI，imageData 长度: {}", imageData.length());
                log.info(">>> imageData 前50字符: {}", imageData.substring(0, Math.min(50, imageData.length())));
                body = Map.of(
                        "message", userMessage,
                        "profile_json", currentProfile.getProfileJson() != null
                                ? currentProfile.getProfileJson() : "",
                        "image_data", imageData
                );
            } else {
                // 无图片时，只传递消息和画像
                log.info(">>> 无图片数据");
                body = Map.of(
                        "message", userMessage,
                        "profile_json", currentProfile.getProfileJson() != null
                                ? currentProfile.getProfileJson() : ""
                );
            }
            String json = objectMapper.writeValueAsString(body);
            log.info(">>> 发送给 Python AI 的 JSON 长度: {}", json.length());

            // 构造 HTTP POST 请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonAiUrl + "/chat"))  // Python AI 服务地址
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))  // 请求超时 60 秒（LLM 调用较慢）
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            // 发送请求并获取响应
            HttpResponse<String> httpResponse = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                // 成功：解析 JSON 响应
                @SuppressWarnings("unchecked")
                Map<String, String> result = objectMapper.readValue(httpResponse.body(), Map.class);
                return result;
            } else {
                log.error("Python AI 返回错误: {} {}", httpResponse.statusCode(), httpResponse.body());
                throw new RuntimeException("Python AI 服务异常: " + httpResponse.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Python AI 调用中断，降级到 Mock", e);
            return fallbackToMock(userMessage);
        } catch (Exception e) {
            log.error("Python AI 调用失败，降级到 Mock: {}", e.getMessage());
            return fallbackToMock(userMessage);
        }
    }

    /**
     * 降级到 Mock 模式 —— Python AI 服务不可用时的备选方案
     *
     * @param userMessage 用户输入的消息
     * @return Map { "response": Mock回复, "profile_json": Mock画像JSON }
     */
    private Map<String, String> fallbackToMock(String userMessage) {
        UserProfile profile = mockAiService.mockProfileExtraction(userMessage);
        String response = mockAiService.mockChatResponse(userMessage, profile);
        try {
            return Map.of(
                    "response", response,
                    "profile_json", objectMapper.writeValueAsString(Map.of(
                            "knowledgeBase", profile.getKnowledgeBase(),
                            "cognitiveStyle", profile.getCognitiveStyle(),
                            "weaknessPoints", profile.getWeaknessPoints(),
                            "learningPace", profile.getLearningPace(),
                            "interestAreas", profile.getInterestAreas(),
                            "shortTermGoal", profile.getShortTermGoal()
                    ))
            );
        } catch (Exception e) {
            return Map.of("response", response, "profile_json", "{}");
        }
    }

    // ===== 工具方法 =====

    /**
     * 发送 SSE 事件 —— 将数据序列化为 JSON 格式并通过 SSE 推送
     *
     * SSE 数据格式：data: {"type":"content","content":"你"}
     * 前端通过 EventSource 监听 onmessage 事件接收
     *
     * @param emitter SSE 发射器
     * @param type    事件类型（conversation_id / content / profile_update / done）
     * @param content 事件内容
     */
    private void sendSSE(SseEmitter emitter, String type, String content) throws IOException {
        // 转义特殊字符（防止 JSON 注入和格式错误）
        String escaped = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        String data = "{\"type\":\"" + type + "\",\"content\":\"" + escaped + "\"}";
        emitter.send(SseEmitter.event().data(data));  // 【Spring Boot】SSE 事件发送
    }

    /**
     * 保存对话消息到 MySQL
     *
     * @param content         消息内容
     * @param role            消息角色（user / assistant）
     * @param conversationId  会话 ID
     * @param userId          用户 ID
     */
    private void saveMessage(String content, String role, String conversationId, Long userId) {
        Conversation conv = new Conversation();
        conv.setContent(content);
        conv.setRole(role);
        conv.setConversationId(conversationId);
        conv.setUserId(userId);
        conv.setTimestamp(LocalDateTime.now());
        conversationRepository.save(conv);  // 【Spring Boot/Data JPA】保存到 MySQL
    }
}
