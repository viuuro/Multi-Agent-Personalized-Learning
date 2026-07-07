package com.edu.agent.service;

import com.edu.agent.config.AiProperties;
import com.edu.agent.controller.dto.SubmissionDetail;
import com.edu.agent.model.AiEvaluation;
import com.edu.agent.model.Task;
import com.edu.agent.model.TaskSubmission;
import com.edu.agent.repository.AiEvaluationRepository;
import com.edu.agent.repository.TaskRepository;
import com.edu.agent.repository.TaskSubmissionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务提交与 AI 评价服务 —— 核心业务逻辑
 *
 * ========== 本模块的完整业务数据流 ==========
 *
 *   ┌──────────────────────────────────────────────────────────────────┐
 *   │  ① 用户提交成果                                                  │
 *   │     POST /api/submissions                                       │
 *   │     → SubmissionController.submit()                             │
 *   │     → SubmissionService.submit()          [主线程，同步]          │
 *   │       ├─ 校验任务存在 + 用户权限                                  │
 *   │       ├─ 保存 TaskSubmission（status=PENDING）                   │
 *   │       └─ 调用 evaluateAsync()             [异步线程池]            │
 *   ├──────────────────────────────────────────────────────────────────┤
 *   │  ② 异步 AI 评价                                                  │
 *   │     evaluateAsync()                                             │
 *   │       ├─ buildPrompt() — 构造提示词（任务描述 + 提交成果）          │
 *   │       ├─ callAiApi()   — 调用 OpenAI 兼容接口                     │
 *   │       │   ├─ 有 API Key → HTTP 调用真实 AI                       │
 *   │       │   └─ 无 API Key → mockEvaluation() 降级                  │
 *   │       ├─ 解析 JSON {score, analysis, suggestion}                │
 *   │       ├─ 保存 AiEvaluation 到数据库                              │
 *   │       └─ 更新 TaskSubmission.status → EVALUATED                 │
 *   ├──────────────────────────────────────────────────────────────────┤
 *   │  ③ 查询结果                                                     │
 *   │     GET /api/submissions/{id}                                   │
 *   │     → SubmissionDetail（含提交内容 + AI 评分/分析/建议）            │
 *   └──────────────────────────────────────────────────────────────────┘
 *
 * ========== AI Prompt 设计 ==========
 *   通过 buildPrompt() 构造提示词，让 AI 从以下四个维度评估：
 *     1. 完成度 — 成果是否完整覆盖了任务要求
 *     2. 质量   — 成果的深度、准确性和条理性
 *     3. 创新性 — 是否有独到的见解或方法
 *     4. 规范性 — 格式和表达是否规范
 *   AI 必须返回严格的 JSON 格式：{ "score": 85, "analysis": "...", "suggestion": "..." }
 *
 * ========== 异常处理策略 ==========
 *   - AI API 超时 → 捕获异常 → status→ERROR + errorMessage 记录
 *   - AI 返回格式错误 → 捕获异常 → status→ERROR + errorMessage 记录
 *   - AI 评分超出 0-100 → 抛出异常 → status→ERROR
 *   - 未配置 API Key → 自动降级 Mock 评价（不调用真实 API）
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Service: 声明为业务服务层 Bean
 *   - @Transactional: 声明式事务管理
 *   - @Async: 异步方法执行（由 AsyncConfig + @EnableAsync 启用）
 *   - 构造器注入: 所有 Repository + AiProperties
 */
@Service  // 【Spring Boot】声明为业务服务 Bean
public class SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionService.class);

    /** 任务数据访问，用于校验任务存在和用户权限 */
    private final TaskRepository taskRepository;
    /** 提交记录数据访问 */
    private final TaskSubmissionRepository submissionRepository;
    /** AI 评价数据访问 */
    private final AiEvaluationRepository evaluationRepository;
    /** AI 配置（API Key、Base URL、Model、超时等） */
    private final AiProperties aiProperties;
    /** JSON 序列化/反序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** Java 11+ 内置 HTTP 客户端，用于调用 AI API */
    private final HttpClient httpClient;

    /**
     * 【Spring Boot】构造器注入 —— Spring 自动装配所有依赖
     */
    public SubmissionService(TaskRepository taskRepository,
                             TaskSubmissionRepository submissionRepository,
                             AiEvaluationRepository evaluationRepository,
                             AiProperties aiProperties) {
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.evaluationRepository = evaluationRepository;
        this.aiProperties = aiProperties;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))  // 连接超时 10 秒
                .build();
    }

    /**
     * 提交任务成果 —— 核心入口方法
     *
     * 流程：
     *   1. 校验任务存在且属于当前用户
     *   2. 校验成果内容不为空
     *   3. 保存 TaskSubmission（status=PENDING）
     *   4. 异步调用 AI 评价（不阻塞 HTTP 响应）
     *
     * @param userId  用户 ID（从 Header X-User-Id 获取）
     * @param taskId  任务 ID（从请求体获取）
     * @param content 提交的成果内容（从请求体获取）
     * @return 提交记录的 ID（前端可用此 ID 轮询查询评价结果）
     * @throws IllegalArgumentException 任务不存在、用户无权限、内容为空
     */
    @Transactional  // 【Spring Boot】声明式事务——保证提交记录保存的原子性
    public Long submit(Long userId, Long taskId, String content) {
        // 1. 校验任务存在并属于当前用户（防止用户提交他人的任务）
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在或无权访问"));

        // 2. 校验成果内容不为空
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("提交内容不能为空");
        }

        // 3. 保存提交记录（初始状态为 PENDING，表示等待 AI 评价）
        TaskSubmission submission = new TaskSubmission();
        submission.setTaskId(taskId);
        submission.setUserId(userId);
        submission.setContent(content.trim());
        submission.setStatus(TaskSubmission.STATUS_PENDING);
        submissionRepository.save(submission);

        log.info(">>> 用户 {} 提交任务 {} 的成果, submissionId={}", userId, taskId, submission.getId());

        // 4. 异步调用 AI 评价（不阻塞当前线程，用户在 GET 查询时获取结果）
        evaluateAsync(submission.getId(), task.getDescription(), content);

        return submission.getId();
    }

    /**
     * 异步调用 AI 进行评价 —— 由 @Async 注解在独立线程池中执行
     *
     * 此方法不会阻塞主线程，Spring 的 AsyncConfig 会为其分配独立线程执行。
     * 评价完成后自动更新 submission 的状态和关联的 ai_evaluation 记录。
     *
     * @param submissionId      提交记录 ID
     * @param taskDescription   任务描述（来自 Task.description，用于 AI 判断完成度）
     * @param submissionContent 用户提交的成果内容（来自请求体）
     */
    @Async  // 【Spring Boot】异步执行 —— 由 AsyncConfig 的 @EnableAsync 启用
    protected void evaluateAsync(Long submissionId, String taskDescription, String submissionContent) {
        log.info(">>> 开始异步 AI 评价, submissionId={}", submissionId);
        try {
            // 1. 构造 AI Prompt
            String prompt = buildPrompt(taskDescription, submissionContent);

            // 2. 调用 AI API（若有 API Key）或 Mock 降级
            Map<String, Object> aiResult = callAiApi(prompt);

            // 3. 解析 AI 返回的 JSON 结果
            Integer score = (Integer) aiResult.get("score");
            String analysis = (String) aiResult.get("analysis");
            String suggestion = (String) aiResult.get("suggestion");

            if (score == null || analysis == null || suggestion == null) {
                throw new RuntimeException("AI 返回结果格式不完整: " + aiResult);
            }

            // 4. 校验评分范围（0-100 百分制）
            if (score < 0 || score > 100) {
                throw new RuntimeException("AI 返回的评分超出范围 (0-100): " + score);
            }

            // 5. 持久化 AI 评价结果
            AiEvaluation evaluation = new AiEvaluation();
            evaluation.setSubmissionId(submissionId);
            evaluation.setScore(score);
            evaluation.setAnalysis(analysis);
            evaluation.setSuggestion(suggestion);
            evaluationRepository.save(evaluation);

            // 6. 更新提交状态为已评价
            TaskSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
            submission.setStatus(TaskSubmission.STATUS_EVALUATED);
            submissionRepository.save(submission);

            log.info(">>> AI 评价完成, submissionId={}, score={}", submissionId, score);

        } catch (Exception e) {
            // 异常处理：AI 超时、格式错误、网络异常等
            log.error(">>> AI 评价失败, submissionId={}, error={}", submissionId, e.getMessage(), e);

            // 标记提交为错误状态，记录错误信息
            try {
                TaskSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
                submission.setStatus(TaskSubmission.STATUS_ERROR);
                submission.setErrorMessage(
                        e.getMessage() != null ? e.getMessage() : "AI 评价调用失败");
                submissionRepository.save(submission);
            } catch (Exception ex) {
                log.error(">>> 更新提交状态为 ERROR 失败: {}", ex.getMessage());
            }
        }
    }

    /**
     * 构造 AI Prompt —— 让模型根据任务描述和提交成果进行评分
     *
     * Prompt 设计原则：
     *   - 明确角色定位：你是一位专业的学习成果评估专家
     *   - 提供完整的任务描述和提交内容
     *   - 指定四个评估维度：完成度、质量、创新性、规范性
     *   - 强制要求 JSON 格式输出（便于后端解析）
     *   - 限定评分范围（0-100 整数）
     *   - 要求使用中文（analysis 和 suggestion 字段）
     *
     * @param taskDescription   任务描述
     * @param submissionContent 用户提交的成果内容
     * @return 完整的 Prompt 字符串
     */
    private String buildPrompt(String taskDescription, String submissionContent) {
        return "你是一位专业的学习成果评估专家。请根据以下任务描述和用户提交的成果，进行评分和分析。\n\n"
                + "【任务描述】\n" + taskDescription + "\n\n"
                + "【用户提交成果】\n" + submissionContent + "\n\n"
                + "请从以下维度进行评估：\n"
                + "1. 完成度：成果是否完整覆盖了任务要求\n"
                + "2. 质量：成果的深度、准确性和条理性\n"
                + "3. 创新性：是否有独到的见解或方法\n"
                + "4. 规范性：格式和表达是否规范\n\n"
                + "请严格按以下 JSON 格式返回（不要包含其他文字）：\n"
                + "{\n"
                + "  \"score\": 85,\n"
                + "  \"analysis\": \"详细分析用户成果的优缺点...\",\n"
                + "  \"suggestion\": \"具体的改进建议...\"\n"
                + "}\n\n"
                + "注意：score 为 0-100 的整数，analysis 和 suggestion 需用中文。";
    }

    /**
     * 调用 OpenAI 兼容的 API 接口
     *
     * 支持任何兼容 OpenAI Chat Completion 格式的 API，包括：
     *   - OpenAI GPT-4o / GPT-4 / GPT-3.5
     *   - DeepSeek Chat
     *   - 通义千问
     *   - 本地部署的 LLaMA / ChatGLM（需兼容接口）
     *
     * 如果未配置 API Key，自动降级到 mockEvaluation()，返回预设的评分数据。
     *
     * @param prompt 发送给 AI 的提示词
     * @return 解析后的 JSON 结果，包含 score/analysis/suggestion 三个字段
     * @throws Exception AI 调用失败、返回格式错误、超时等
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callAiApi(String prompt) throws Exception {
        String apiKey = aiProperties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            // 无 API Key 时使用 Mock 评价（降级模式）
            log.warn("AI API Key 未配置，使用 Mock 评价");
            return mockEvaluation(prompt);
        }

        // 构造请求体（OpenAI Chat Completion 格式）
        Map<String, Object> requestBody = Map.of(
                "model", aiProperties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "你是一个专业的编程作业评分助手。"),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.3,     // 低温度使输出更稳定
                "max_tokens", 2000       // 确保有足够的输出长度
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        // 构造 HTTP POST 请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiProperties.getBaseUrl() + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(aiProperties.getTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        // 发送请求
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("AI API 调用失败, status=" + response.statusCode()
                    + ", body=" + response.body());
        }

        // 解析 OpenAI 标准响应格式
        Map<String, Object> responseMap = objectMapper.readValue(response.body(),
                new TypeReference<Map<String, Object>>() {});

        // 提取 choices[0].message.content（标准 OpenAI 响应结构）
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("AI 返回的 choices 为空");
        }
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = (String) message.get("content");

        if (content == null || content.isBlank()) {
            throw new RuntimeException("AI 返回的 content 为空");
        }

        // 清理可能的 Markdown 代码块标记（AI 有时会用 ```json 包裹 JSON）
        content = content.trim();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        content = content.trim();

        // 解析 JSON
        Map<String, Object> result = objectMapper.readValue(content,
                new TypeReference<Map<String, Object>>() {});

        // 类型转换：score 可能是 Integer 或 Double（某些 API 返回浮点数）
        Object scoreObj = result.get("score");
        if (scoreObj instanceof Number) {
            result.put("score", ((Number) scoreObj).intValue());
        }

        return result;
    }

    /**
     * Mock 评价 —— 当未配置 API Key 时的降级方案
     *
     * 返回固定评分（85 分）和预设的分析/建议文本，
     * 确保开发阶段和演示环境可以正常运行 AI 评价功能。
     *
     * @param prompt 原始 Prompt（Mock 模式下不使用，仅用于参数一致性）
     * @return 模拟的 AI 评价结果
     */
    private Map<String, Object> mockEvaluation(String prompt) {
        log.info(">>> 使用 Mock AI 评价");
        return Map.of(
                "score", 85,
                "analysis", "用户提交的成果基本完成了任务要求，内容结构清晰，逻辑性较强。"
                        + "在关键概念的理解上表现良好，能够正确应用所学知识。"
                        + "建议在深度和细节方面进一步加强。",
                "suggestion", "1. 可以增加更多实际案例来佐证观点\n"
                        + "2. 建议补充代码示例或实践验证\n"
                        + "3. 部分表述可以更加精确和专业化"
        );
    }

    // ===== 查询方法 =====

    /**
     * 查询提交详情（包含 AI 评价结果）
     *
     * 同时校验当前用户是否有权查看该提交记录。
     *
     * @param submissionId 提交记录 ID
     * @param userId       当前用户 ID（用于权限校验）
     * @return 提交详情 DTO（包含提交内容 + AI 评分/分析/建议）
     * @throws IllegalArgumentException 提交不存在或用户无权限
     */
    public SubmissionDetail getSubmissionDetail(Long submissionId, Long userId) {
        TaskSubmission submission = submissionRepository.findByIdAndUserId(submissionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("提交记录不存在或无权访问"));

        // AI 评价可能尚未完成（status=PENDING）或失败（status=ERROR），此时 evaluation 为 null
        AiEvaluation evaluation = evaluationRepository.findBySubmissionId(submissionId).orElse(null);
        return SubmissionDetail.from(submission, evaluation);
    }

    /**
     * 查询指定任务的所有提交（按时间倒序，最新的在前）
     *
     * 先校验用户对任务的访问权限，再查询所有提交记录。
     * 每个提交记录都会附带对应的 AI 评价结果（如果有）。
     *
     * @param taskId 任务 ID
     * @param userId 当前用户 ID（用于权限校验）
     * @return 提交详情列表（每个元素包含提交内容 + AI 评价）
     * @throws IllegalArgumentException 任务不存在或用户无权限
     */
    public List<SubmissionDetail> getSubmissionsByTask(Long taskId, Long userId) {
        // 校验任务存在且用户有权限
        if (!taskRepository.findByIdAndUserId(taskId, userId).isPresent()) {
            throw new IllegalArgumentException("任务不存在或无权访问");
        }

        List<TaskSubmission> submissions = submissionRepository.findByTaskIdOrderBySubmissionTimeDesc(taskId);

        return submissions.stream()
                .map(sub -> {
                    AiEvaluation evaluation = evaluationRepository.findBySubmissionId(sub.getId()).orElse(null);
                    return SubmissionDetail.from(sub, evaluation);
                })
                .collect(Collectors.toList());
    }
}