package com.edu.agent.service;

import com.edu.agent.controller.dto.SubmissionDetail;
import com.edu.agent.model.AiEvaluation;
import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.LearningPlanEntity;
import com.edu.agent.model.Task;
import com.edu.agent.model.TaskSubmission;
import com.edu.agent.repository.AiEvaluationRepository;
import com.edu.agent.repository.LearningPlanRepository;
import com.edu.agent.repository.TaskRepository;
import com.edu.agent.repository.TaskSubmissionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
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
 *   │       ├─ 调用 Python AI /evaluate → MiMo-v2.5                    │
 *   │       ├─ 提取评分、薄弱点和下一步行动                            │
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
 *   - 仅显式开启 AI_MOCK_ENABLED 时使用 Mock 评价
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Service: 声明为业务服务层 Bean
 *   - @Transactional: 声明式事务管理
 *   - 专用 Executor: 异步执行评分，并在应用重启后恢复 PENDING 任务
 *   - 构造器注入: Repository、记忆/画像服务与专用 Executor
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
    private final LearningPlanRepository learningPlanRepository;
    private final Executor submissionExecutor;
    private final ConversationSessionService conversationSessionService;
    private final ProfileEvidenceService profileEvidenceService;
    private final ProfileService profileService;
    private final boolean mockMode;
    @Value("${python.ai.url:http://localhost:8000}")
    private String pythonAiUrl;
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
                             LearningPlanRepository learningPlanRepository,
                             @Qualifier("submissionExecutor") Executor submissionExecutor,
                             ConversationSessionService conversationSessionService,
                             ProfileEvidenceService profileEvidenceService,
                             ProfileService profileService,
                             @Value("${ai.mock-enabled:false}") boolean mockMode) {
        this.taskRepository = taskRepository;
        this.submissionRepository = submissionRepository;
        this.evaluationRepository = evaluationRepository;
        this.learningPlanRepository = learningPlanRepository;
        this.submissionExecutor = submissionExecutor;
        this.conversationSessionService = conversationSessionService;
        this.profileEvidenceService = profileEvidenceService;
        this.profileService = profileService;
        this.mockMode = mockMode;

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
    public Long submit(Long userId, Long taskId, String content) {
        // 1. 校验任务存在并属于当前用户（防止用户提交他人的任务）
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在或无权访问"));

        // 2. 校验成果内容不为空
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("提交内容不能为空");
        }

        return persistAndSchedule(userId, task, null, null, null, content);
    }

    /** 按当前对话的学习计划创建/复用成果任务并持久化提交。 */
    public Long submit(Long userId,
                       String conversationId,
                       String fileName,
                       Long fileSize,
                       String content) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        LearningPlanEntity planEntity = learningPlanRepository
                .findFirstByUserIdAndConversationIdOrderByUpdatedAtDesc(userId, conversationId)
                .orElseThrow(() -> new IllegalArgumentException("当前对话尚未生成学习计划"));

        Task task = taskRepository.findByUserIdAndPlanId(userId, planEntity.getId()).stream()
                .findFirst()
                .orElseGet(() -> {
                    Task created = new Task();
                    created.setUserId(userId);
                    created.setPlanId(planEntity.getId());
                    created.setDescription(buildPlanSubmissionDescription(planEntity));
                    created.setStatus("PENDING");
                    return taskRepository.save(created);
                });

        return persistAndSchedule(userId, task, conversationId, fileName, fileSize, content);
    }

    private Long persistAndSchedule(Long userId,
                                    Task task,
                                    String conversationId,
                                    String fileName,
                                    Long fileSize,
                                    String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("提交内容不能为空");
        }
        TaskSubmission submission = new TaskSubmission();
        submission.setTaskId(task.getId());
        submission.setUserId(userId);
        submission.setConversationId(conversationId);
        submission.setFileName(fileName);
        submission.setFileSize(fileSize);
        submission.setContent(content.trim());
        submission.setStatus(TaskSubmission.STATUS_PENDING);
        submission = submissionRepository.save(submission);

        Long submissionId = submission.getId();
        String submittedContent = submission.getContent();
        submissionExecutor.execute(() -> evaluateSubmission(
                submissionId, task.getDescription(), submittedContent));
        log.info(">>> 用户 {} 提交成果, conversationId={}, taskId={}, submissionId={}",
                userId, conversationId, task.getId(), submissionId);
        return submissionId;
    }

    private String buildPlanSubmissionDescription(LearningPlanEntity entity) {
        try {
            LearningPlan plan = objectMapper.readValue(entity.getPlanJson(), LearningPlan.class);
            String topics = plan.getWeeks().stream()
                    .map(LearningPlan.PlanWeek::getTopic)
                    .filter(topic -> topic != null && !topic.isBlank())
                    .limit(4)
                    .collect(Collectors.joining("、"));
            if (!topics.isBlank()) return "提交以下学习计划的阶段性成果：" + topics;
        } catch (Exception ignored) {
            // 旧版计划 JSON 不完整时使用通用描述。
        }
        return "提交当前学习计划的阶段性学习成果";
    }

    /**
     * 异步调用 AI 进行评价 —— 由专用线程池执行
     *
     * 此方法不会阻塞提交请求，AsyncConfig 中的线程池负责执行。
     * 评价完成后自动更新 submission 的状态和关联的 ai_evaluation 记录。
     *
     * @param submissionId      提交记录 ID
     * @param taskDescription   任务描述（来自 Task.description，用于 AI 判断完成度）
     * @param submissionContent 用户提交的成果内容（来自请求体）
     */
    private void evaluateSubmission(Long submissionId, String taskDescription, String submissionContent) {
        log.info(">>> 开始异步 AI 评价, submissionId={}", submissionId);
        try {
            TaskSubmission submission = submissionRepository.findById(submissionId).orElseThrow();
            Map<String, Object> aiResult = mockMode
                    ? mockEvaluation(submissionContent)
                    : callPythonEvaluation(taskDescription, submissionContent, submission);

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
            List<String> weaknesses = stringList(aiResult.get("weaknesses"));
            List<String> recommendedActions = stringList(aiResult.get("recommended_actions"));
            evaluation.setWeaknessesJson(objectMapper.writeValueAsString(weaknesses));
            evaluation.setRecommendedActionsJson(objectMapper.writeValueAsString(recommendedActions));
            evaluationRepository.save(evaluation);

            // 6. 更新提交状态为已评价
            submission.setStatus(TaskSubmission.STATUS_EVALUATED);
            submissionRepository.save(submission);

            try {
                applyEvaluationToLearningLoop(submission, score, analysis, suggestion, weaknesses);
            } catch (Exception loopError) {
                log.warn("成果评价已保存，但学习闭环更新失败: {}", loopError.getMessage());
            }

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

    private Map<String, Object> callPythonEvaluation(String taskDescription,
                                                     String submissionContent,
                                                     TaskSubmission submission) throws Exception {
        String profileJson = "";
        String planJson = "";
        if (submission.getConversationId() != null && !submission.getConversationId().isBlank()) {
            profileJson = profileService.getCurrentProfile(
                    submission.getUserId(), submission.getConversationId()).getProfileJson();
            planJson = learningPlanRepository
                    .findFirstByUserIdAndConversationIdOrderByUpdatedAtDesc(
                            submission.getUserId(), submission.getConversationId())
                    .map(LearningPlanEntity::getPlanJson)
                    .orElse("");
        }
        Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
        requestBody.put("task_description", taskDescription);
        requestBody.put("submission_content", submissionContent);
        requestBody.put("profile_json", profileJson == null ? "" : profileJson);
        requestBody.put("current_plan_json", planJson);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pythonAiUrl + "/evaluate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Python AI 成果评价失败, status=" + response.statusCode());
        }
        Map<String, Object> result = objectMapper.readValue(
                response.body(), new TypeReference<Map<String, Object>>() {});
        Object score = result.get("score");
        if (score instanceof Number number) result.put("score", number.intValue());
        return result;
    }

    private void applyEvaluationToLearningLoop(TaskSubmission submission,
                                               int score,
                                               String analysis,
                                               String suggestion,
                                               List<String> weaknesses) throws Exception {
        String conversationId = submission.getConversationId();
        if (conversationId == null || conversationId.isBlank()) return;
        conversationSessionService.recordSubmissionFeedback(
                submission.getUserId(), conversationId, score, analysis, suggestion);
        if (weaknesses.isEmpty()) return;
        double confidence = score < 80 ? 0.82 : 0.68;
        Map<String, Object> evidence = Map.of(
                "dimension", "weaknessPoints",
                "value", weaknesses,
                "evidence", "学习成果评分 " + score + " 分：" + analysis,
                "confidence", confidence,
                "scope", "LONG_TERM",
                "action", "merge"
        );
        java.util.Set<String> accepted = profileEvidenceService.persist(
                submission.getUserId(), conversationId, null,
                objectMapper.writeValueAsString(List.of(evidence)));
        if (accepted.contains("weaknessPoints")) {
            profileService.applySubmissionWeaknesses(
                    submission.getUserId(), conversationId, weaknesses);
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(String::valueOf).map(String::trim)
                .filter(item -> !item.isBlank()).distinct().limit(6).toList();
    }

    private Map<String, Object> mockEvaluation(String ignored) {
        return Map.of(
                "score", 85,
                "analysis", "成果结构完整，已覆盖主要任务要求；当前为显式 Mock 评价。",
                "suggestion", "补充一个可验证的实践案例，并记录验证结果。",
                "weaknesses", List.of("实践验证不足"),
                "recommended_actions", List.of("补充实践案例", "记录验证结果")
        );
    }

    /** 后端重启后继续处理已经落库但尚未完成的评分任务。 */
    @EventListener(ApplicationReadyEvent.class)
    public void resumePendingEvaluations() {
        List<TaskSubmission> pending = submissionRepository.findByStatus(TaskSubmission.STATUS_PENDING);
        if (!pending.isEmpty()) {
            log.info(">>> 恢复 {} 个未完成的成果评分任务", pending.size());
        }
        for (TaskSubmission submission : pending) {
            if (evaluationRepository.findBySubmissionId(submission.getId()).isPresent()) {
                submission.setStatus(TaskSubmission.STATUS_EVALUATED);
                submissionRepository.save(submission);
                continue;
            }
            taskRepository.findByIdAndUserId(submission.getTaskId(), submission.getUserId())
                    .ifPresentOrElse(
                            task -> submissionExecutor.execute(() -> evaluateSubmission(
                                    submission.getId(), task.getDescription(), submission.getContent())),
                            () -> {
                                submission.setStatus(TaskSubmission.STATUS_ERROR);
                                submission.setErrorMessage("关联任务不存在，无法恢复评分");
                                submissionRepository.save(submission);
                            });
        }
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

    /** 查询指定对话下的成果与评分，刷新或切换对话时用于恢复 UI。 */
    public List<SubmissionDetail> getSubmissionsByConversation(Long userId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        return submissionRepository
                .findByUserIdAndConversationIdOrderBySubmissionTimeDesc(userId, conversationId)
                .stream()
                .map(submission -> SubmissionDetail.from(submission,
                        evaluationRepository.findBySubmissionId(submission.getId()).orElse(null)))
                .collect(Collectors.toList());
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
