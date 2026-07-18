package com.edu.agent.service;

import com.edu.agent.model.Conversation;
import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.LearningPlanEntity;
import com.edu.agent.model.UserProfile;
import com.edu.agent.repository.ConversationRepository;
import com.edu.agent.repository.LearningPlanRepository;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    /** 学习计划编排服务，用于处理对话中的计划更新意图 */
    private final AgentOrchestrationService orchestrationService;
    /** 学习计划持久化仓库 */
    private final LearningPlanRepository learningPlanRepository;
    /** 对话标题等会话级元数据 */
    private final ConversationSessionService conversationSessionService;
    private final ProfileEvidenceService profileEvidenceService;
    private final AgentDecisionService agentDecisionService;
    private final LearningPlanVersionService planVersionService;
    private final KnowledgeBaseService knowledgeBaseService;
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
                       AgentOrchestrationService orchestrationService,
                       LearningPlanRepository learningPlanRepository,
                       ConversationSessionService conversationSessionService,
                       ProfileEvidenceService profileEvidenceService,
                       AgentDecisionService agentDecisionService,
                       LearningPlanVersionService planVersionService,
                       KnowledgeBaseService knowledgeBaseService,
                       @Value("${ai.mock-enabled:false}") boolean mockMode) {
        this.conversationRepository = conversationRepository;
        this.profileService = profileService;
        this.mockAiService = mockAiService;
        this.orchestrationService = orchestrationService;
        this.learningPlanRepository = learningPlanRepository;
        this.conversationSessionService = conversationSessionService;
        this.profileEvidenceService = profileEvidenceService;
        this.agentDecisionService = agentDecisionService;
        this.planVersionService = planVersionService;
        this.knowledgeBaseService = knowledgeBaseService;
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
    public SseEmitter handleChat(String userMessage,
                                 String displayMessage,
                                 String conversationId,
                                 String imageData,
                                 String attachmentName,
                                 String attachmentType,
                                 Long userId) {
        // Step 1: 生成会话 ID（如果前端未提供）
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        final String convId = conversationId;
        // 创建 SSE 发射器（0L = 不设置超时，由后端控制完成时机）
        SseEmitter emitter = new SseEmitter(0L);

        // Step 2: 保存用户消息到数据库。文件解析全文只写入 aiContent，不污染历史对话展示。
        if (userId != null) {
            conversationSessionService.ensureSession(userId, convId);
        }
        String visibleMessage = displayMessage == null || displayMessage.isBlank()
                ? userMessage : displayMessage;
        Conversation userRecord = saveMessage(visibleMessage, userMessage, "user", convId, userId,
                attachmentName, attachmentType,
                "image".equals(attachmentType) ? imageData : null);

        // Step 3: 提取画像 + 生成回复
        UserProfile extractedProfile;
        String fullResponse;
        String planAction = "none";
        String planRevisionRequest = userMessage;
        String revisionScopeJson = "{}";
        Map<String, Object> result;
        if (mockMode) {
            // Mock 模式：使用预设数据
            extractedProfile = mockAiService.mockProfileExtraction(userMessage);
            fullResponse = mockAiService.mockChatResponse(userMessage, extractedProfile);
            planAction = detectPlanAction(userMessage);
            result = buildMockDecisionResult(userMessage, convId, planAction, fullResponse);
        } else {
            // 真实模式：调用 Python AI 服务（FastAPI + LangChain → MiMo-v2.5 API）
            result = callPythonChat(userMessage, imageData, userId, convId);
            fullResponse = String.valueOf(result.getOrDefault("response", ""));
            String profileJson = String.valueOf(result.getOrDefault("profile_json", "{}"));
            planAction = String.valueOf(result.getOrDefault("plan_action", "none"));
            planRevisionRequest = String.valueOf(result.getOrDefault(
                    "plan_revision_request", userMessage));
            revisionScopeJson = String.valueOf(result.getOrDefault("revision_scope_json", "{}"));
            // 将 Python 返回的 JSON 解析为 UserProfile 对象
            extractedProfile = profileService.parseProfileJson(profileJson);
        }

        // Step 4: 更新用户画像到 MySQL
        String evidenceJson = String.valueOf(result.getOrDefault("profile_evidence_json", "[]"));
        java.util.Set<String> supportedDimensions = profileEvidenceService.persist(
                userId, convId, userRecord == null ? null : userRecord.getId(), evidenceJson);
        UserProfile savedProfile = mockMode
                ? profileService.updateProfile(extractedProfile, userId, convId)
                : profileService.updateProfileWithSupportedDimensions(
                        extractedProfile, userId, convId, supportedDimensions);
        persistIntelligenceState(userId, convId,
                userRecord == null ? null : userRecord.getId(), result);

        // Step 5-7: 在新线程中执行 SSE 流式推送（避免阻塞主线程）
        final String finalResponse = fullResponse;
        final String finalPlanAction = planAction;
        final String finalPlanRevisionRequest = planRevisionRequest;
        final String finalRevisionScopeJson = revisionScopeJson;
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

                // 用户在对话中明确要求修订计划时，直接生成、持久化并推送新计划。
                if (isPlanAction(finalPlanAction)) {
                    LearningPlan updatedPlan = regenerateAndPersistPlan(
                            userId, convId, finalPlanRevisionRequest,
                            finalPlanAction, finalRevisionScopeJson);
                    sendSSE(emitter, "plan_update", objectMapper.writeValueAsString(updatedPlan));
                }

                // Step 7b: 保存 AI 回复到 MySQL
                saveMessage(finalResponse, finalResponse, "assistant", convId, userId,
                        null, null, null);
                // Step 7a: 推送完成信号（前端用于结束流式状态）
                sendSSE(emitter, "done", "");
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
    private Map<String, Object> callPythonChat(String userMessage, String imageData, Long userId,
                                                String conversationId) {
        try {
            // 获取当前画像 JSON 传给 Python（用于画像增量更新）
            UserProfile currentProfile = profileService.getCurrentProfile(userId, conversationId);
            Map<String, Object> body = new HashMap<>();
            body.put("message", userMessage);
            body.put("profile_json", currentProfile.getProfileJson() != null
                    ? currentProfile.getProfileJson() : "");
            body.put("conversation_context", buildConversationContext(userId, conversationId));
            body.put("current_plan_json", learningPlanRepository
                    .findFirstByUserIdAndConversationIdOrderByUpdatedAtDesc(userId, conversationId)
                    .map(LearningPlanEntity::getPlanJson)
                    .orElse(""));
            conversationSessionService.findSession(userId, conversationId).ifPresent(session -> {
                body.put("memory_summary", valueOrEmpty(session.getMemorySummary()));
                body.put("temporary_state_json", valueOrEmpty(session.getTemporaryStateJson()));
                body.put("temporary_state_updated_at", session.getUpdatedAt() == null
                        ? "" : session.getUpdatedAt().toString());
                body.put("dialogue_state", valueOrEmpty(session.getDialogueState()));
                body.put("pending_question", valueOrEmpty(session.getPendingQuestion()));
            });
            body.putIfAbsent("memory_summary", "");
            body.putIfAbsent("temporary_state_json", "{}");
            body.putIfAbsent("temporary_state_updated_at", "");
            body.putIfAbsent("dialogue_state", "");
            body.putIfAbsent("pending_question", "");
            body.put("profile_evidence_json",
                    profileEvidenceService.buildEvidenceJson(userId, conversationId));
            body.put("recent_responses_json", buildRecentAssistantResponses(userId, conversationId));
            String knowledgeContext = knowledgeBaseService.buildContext(
                    userId, conversationId, userMessage, 6);
            body.put("knowledge_context", knowledgeContext);
            if (!knowledgeContext.isBlank()) {
                log.info("知识库命中: userId={}, conversationId={}, contextLength={}",
                        userId, conversationId, knowledgeContext.length());
            }
            if (imageData != null && !imageData.isEmpty()) {
                // 有图片时，传递图片数据
                log.info(">>> 传递图片数据给 Python AI，imageData 长度: {}", imageData.length());
                log.info(">>> imageData 前50字符: {}", imageData.substring(0, Math.min(50, imageData.length())));
                body.put("image_data", imageData);
            } else {
                log.info(">>> 无图片数据");
            }
            String json = objectMapper.writeValueAsString(body);
            log.info(">>> 发送给 Python AI 的 JSON 长度: {}", json.length());

            // 构造 HTTP POST 请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonAiUrl + "/chat"))  // Python AI 服务地址
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(150))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            // 发送请求并获取响应
            HttpResponse<String> httpResponse = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                // 成功：解析 JSON 响应
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(httpResponse.body(), Map.class);
                return result;
            } else {
                log.error("Python AI 返回错误: {} {}", httpResponse.statusCode(), httpResponse.body());
                throw new RuntimeException("Python AI 服务异常: " + httpResponse.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Python AI 调用中断，降级到 Mock", e);
            return fallbackToMock(userMessage, userId, conversationId);
        } catch (Exception e) {
            log.error("Python AI 调用失败，降级到 Mock: {}", e.getMessage());
            return fallbackToMock(userMessage, userId, conversationId);
        }
    }

    /**
     * 降级到 Mock 模式 —— Python AI 服务不可用时的备选方案
     *
     * @param userMessage 用户输入的消息
     * @return Map { "response": Mock回复, "profile_json": Mock画像JSON }
     */
    private Map<String, Object> fallbackToMock(String userMessage, Long userId, String conversationId) {
        UserProfile profile = mockAiService.mockProfileExtraction(userMessage);
        String response = mockAiService.mockChatResponse(userMessage, profile);
        Map<String, Object> result = buildMockDecisionResult(
                userMessage, conversationId, detectPlanAction(userMessage), response);
        try {
            result.put("profile_json", objectMapper.writeValueAsString(Map.of(
                            "knowledgeBase", profile.getKnowledgeBase(),
                            "cognitiveStyle", profile.getCognitiveStyle(),
                            "weaknessPoints", profile.getWeaknessPoints(),
                            "learningPace", profile.getLearningPace(),
                            "interestAreas", profile.getInterestAreas(),
                            "shortTermGoal", profile.getShortTermGoal()
                    )));
        } catch (Exception e) {
            result.put("profile_json", "{}");
        }
        conversationSessionService.findSession(userId, conversationId).ifPresent(session ->
                result.put("memory_summary", valueOrEmpty(session.getMemorySummary())));
        return result;
    }

    /** 根据当前完整对话生成适合展示在顶栏的简短标题。 */
    public String generateConversationTitle(String conversationContext) {
        String context = conversationContext == null ? "" : conversationContext.trim();
        if (context.isEmpty()) {
            return "新对话";
        }
        try {
            String json = objectMapper.writeValueAsString(Map.of("conversation_context", context));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonAiUrl + "/title"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(45))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                String title = String.valueOf(result.getOrDefault("title", "")).trim();
                if (!title.isBlank()) {
                    return title.length() > 24 ? title.substring(0, 24) : title;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("会话标题分析失败，使用本地标题: {}", e.getMessage());
        }
        return fallbackConversationTitle(context);
    }

    private String fallbackConversationTitle(String context) {
        String candidate = "";
        for (String line : context.split("\\R")) {
            if (line.startsWith("用户：")) {
                candidate = line.substring(3).trim();
            }
        }
        if (candidate.isBlank()) {
            candidate = context;
        }
        candidate = candidate
                .replaceAll("(?s)```.*?```", "")
                .replaceAll("[\\s#>*_`\\[\\](){}]+", "")
                .replaceFirst("^(请问|请帮我|帮我|我想要?|我要|如何|怎么)", "")
                .replaceAll("^[，。！？:：;；]+|[，。！？:：;；]+$", "");
        if (candidate.isBlank()) {
            return "新对话";
        }
        return candidate.length() > 16 ? candidate.substring(0, 16) : candidate;
    }

    /** 组装最近完整对话，同时保留用户与智能体消息及其时序。 */
    private String buildConversationContext(Long userId, String conversationId) {
        try {
            List<Conversation> recent = new ArrayList<>(conversationRepository
                    .findByUserIdAndConversationIdOrderByTimestampDesc(userId, conversationId));
            if (recent.size() > 16) {
                recent = new ArrayList<>(recent.subList(0, 16));
            }
            Collections.reverse(recent);
            StringBuilder context = new StringBuilder();
            for (Conversation conversation : recent) {
                String role = "assistant".equals(conversation.getRole()) ? "智能体" : "用户";
                String contextContent = conversation.getAiContent() == null
                        || conversation.getAiContent().isBlank()
                        ? conversation.getContent() : conversation.getAiContent();
                context.append(role).append("：")
                        .append(contextContent).append('\n');
            }
            return context.toString().trim();
        } catch (Exception e) {
            log.warn("获取完整对话上下文失败: {}", e.getMessage());
            return "";
        }
    }

    /** 与 Python 端意图识别保持一致，供 Mock/降级模式使用。 */
    private String detectPlanAction(String message) {
        if (message == null) {
            return "none";
        }
        String text = message.replaceAll("\\s+", "");
        boolean mentionsPlan = text.contains("计划");
        if (text.matches(".*(调整|修改|替换|换).*(资源|课程|教程|视频|资料).*")
                || text.matches(".*(资源|课程|教程|视频|资料).*(调整|修改|替换|换).*")) {
            return "adjust_resources";
        }
        if (text.matches(".*第[一二三四1234]周.*")) return "modify_week";
        if (mentionsPlan && text.matches(".*(难度|太难|太简单|更难|简单一点).*") ) {
            return "adjust_difficulty";
        }
        if (mentionsPlan && text.matches(".*(节奏|周期|时间|快一点|慢一点).*") ) {
            return "adjust_pace";
        }
        if (mentionsPlan && text.matches(".*(方向|主题|改学|换成|转到).*") ) {
            return "change_direction";
        }
        if (mentionsPlan && text.matches(".*(重新|更新|调整|修改|重做|改一下|再生成|换一份|换一个).*") ) {
            return "full_regenerate";
        }
        if (mentionsPlan && text.matches(".*(生成|制定|做一份|安排).*") ) return "create";
        return "none";
    }

    /** 根据用户修订要求更新最近计划，并保存为当前版本。 */
    private LearningPlan regenerateAndPersistPlan(Long userId, String conversationId,
                                                  String revisionRequest,
                                                  String revisionAction,
                                                  String revisionScopeJson) {
        LearningPlanEntity current = planVersionService.getCurrentEntity(userId, conversationId)
                .orElse(null);
        String existingPlanJson = current == null ? "" : current.getPlanJson();
        LearningPlan updatedPlan = orchestrationService.generatePlan(
                userId, conversationId, existingPlanJson, revisionRequest,
                revisionAction, revisionScopeJson);
        try {
            planVersionService.saveNewVersion(userId, conversationId, updatedPlan,
                    revisionAction, revisionRequest);
            log.info("对话触发的学习计划已更新, userId={}, action={}", userId, revisionAction);
        } catch (Exception e) {
            log.error("对话触发的计划持久化失败: {}", e.getMessage(), e);
            throw new IllegalStateException("学习计划更新后保存失败", e);
        }
        return updatedPlan;
    }

    private boolean isPlanAction(String action) {
        return action != null && !action.isBlank() && !"none".equalsIgnoreCase(action);
    }

    private Map<String, Object> buildMockDecisionResult(String userMessage,
                                                        String conversationId,
                                                        String planAction,
                                                        String response) {
        Map<String, Object> result = new HashMap<>();
        result.put("response", response);
        result.put("intent", isPlanAction(planAction)
                ? ("create".equals(planAction) ? "PLAN_CREATE" : "PLAN_REVISE") : "STUDY_QA");
        result.put("dialogue_state", isPlanAction(planAction) ? "REVISING_PLAN" : "ANSWERING");
        result.put("plan_action", planAction);
        result.put("requested_plan_action", planAction);
        result.put("plan_revision_request", userMessage);
        result.put("revision_scope_json", "{}");
        result.put("needs_clarification", false);
        result.put("clarifying_question", "");
        result.put("temporary_state_json", "{}");
        result.put("profile_evidence_json", "[]");
        result.put("memory_summary", "用户最新诉求：" + userMessage);
        result.put("quality_json", "{\"score\":70,\"issues\":[\"mock_fallback\"],\"reviewed\":false}");
        return result;
    }

    private void persistIntelligenceState(Long userId,
                                          String conversationId,
                                          Long messageId,
                                          Map<String, Object> result) {
        String needsClarification = String.valueOf(result.getOrDefault("needs_clarification", false));
        String pendingQuestion = Boolean.parseBoolean(needsClarification)
                ? String.valueOf(result.getOrDefault("clarifying_question", "")) : "";
        conversationSessionService.updateIntelligence(
                userId,
                conversationId,
                String.valueOf(result.getOrDefault("memory_summary", "")),
                String.valueOf(result.getOrDefault("temporary_state_json", "{}")),
                String.valueOf(result.getOrDefault("intent", "STUDY_QA")),
                String.valueOf(result.getOrDefault("dialogue_state", "ANSWERING")),
                pendingQuestion,
                String.valueOf(result.getOrDefault("quality_json", "{}")));
        agentDecisionService.record(userId, conversationId, messageId, result);
    }

    private String buildRecentAssistantResponses(Long userId, String conversationId) {
        try {
            List<String> responses = conversationRepository
                    .findByUserIdAndConversationIdOrderByTimestampDesc(userId, conversationId)
                    .stream()
                    .filter(item -> "assistant".equals(item.getRole()))
                    .limit(3)
                    .map(Conversation::getContent)
                    .toList();
            return objectMapper.writeValueAsString(responses);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
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
    private Conversation saveMessage(String content,
                             String aiContent,
                             String role,
                             String conversationId,
                             Long userId,
                             String attachmentName,
                             String attachmentType,
                             String attachmentData) {
        Conversation conv = new Conversation();
        conv.setContent(content);
        conv.setAiContent(aiContent);
        conv.setRole(role);
        conv.setConversationId(conversationId);
        conv.setUserId(userId);
        conv.setAttachmentName(attachmentName);
        conv.setAttachmentType(attachmentType);
        conv.setAttachmentData(attachmentData);
        conv.setTimestamp(LocalDateTime.now());
        return conversationRepository.save(conv);
    }
}
