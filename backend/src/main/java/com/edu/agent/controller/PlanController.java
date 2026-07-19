package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.LearningPlanEntity;
import com.edu.agent.repository.LearningPlanRepository;
import com.edu.agent.repository.TaskRepository;
import com.edu.agent.service.AgentOrchestrationService;
import com.edu.agent.service.LearningPlanVersionService;
import com.edu.agent.service.RequestRateLimiter;
import com.edu.agent.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.Optional;
import java.util.List;

/**
 * 计划控制器 —— 生成 / 读取 / 保存 学习计划
 *
 * - POST /api/plan  触发 AI 生成并持久化
 * - GET  /api/plan  读取用户最近一次计划
 * - PUT  /api/plan  保存用户编辑后的计划
 */
@RestController
@RequestMapping("/api")
public class PlanController {

    private static final Logger log = LoggerFactory.getLogger(PlanController.class);

    private final AgentOrchestrationService orchestrationService;
    private final LearningPlanRepository planRepository;
    private final TaskRepository taskRepository;
    private final LearningPlanVersionService planVersionService;
    private final RequestRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public PlanController(AgentOrchestrationService orchestrationService,
                          LearningPlanRepository planRepository,
                          TaskRepository taskRepository,
                          LearningPlanVersionService planVersionService,
                          RequestRateLimiter rateLimiter,
                          ObjectMapper objectMapper) {
        this.orchestrationService = orchestrationService;
        this.planRepository = planRepository;
        this.taskRepository = taskRepository;
        this.planVersionService = planVersionService;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    /** 生成计划并持久化 */
    @PostMapping("/plan")
    public ApiResponse<LearningPlan> generatePlan(@RequestBody Map<String, Object> body,
                                                   Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        if (!rateLimiter.tryAcquire("plan:" + userId, 5, 60)) {
            return ApiResponse.error(429, "计划生成过于频繁，请稍后再试");
        }
        String conversationId = String.valueOf(body.getOrDefault("conversationId", "")).trim();
        if (conversationId.isBlank()) {
            return ApiResponse.error(400, "缺少 conversationId");
        }
        log.info(">>> POST /api/plan —— 触发多智能体协同, userId={}, conversationId={}",
                userId, conversationId);

        LearningPlanEntity existing = planVersionService.getCurrentEntity(userId, conversationId).orElse(null);
        boolean hasExistingPlan = existing != null;
        LearningPlan plan = hasExistingPlan
                ? orchestrationService.generatePlan(
                        userId, conversationId, existing.getPlanJson(),
                        "结合当前画像与最新对话重新生成计划，保留仍有价值的安排",
                        "full_regenerate", "{}")
                : orchestrationService.generatePlan(userId, conversationId);
        log.info(">>> 计划生成完成。共 {} 周。", plan.getWeeks().size());

        // 持久化到数据库
        try {
            planVersionService.saveNewVersion(userId, conversationId, plan,
                    hasExistingPlan ? "full_regenerate" : "create",
                    hasExistingPlan ? "用户通过按钮重新生成计划" : "用户首次生成计划");
        } catch (Exception e) {
            log.error("计划持久化失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "计划已生成，但持久化失败，请稍后重试");
        }

        return ApiResponse.success("多智能体协同完成，学习计划已生成", plan);
    }

    /** 读取用户最近一次计划 */
    @GetMapping("/plan")
    public ApiResponse<LearningPlan> getPlan(@RequestParam(required = false) Long userId,
                                             Authentication authentication,
                                             @RequestParam String conversationId) {
        userId = CurrentUser.id(authentication);
        Optional<LearningPlanEntity> entity = planRepository
                .findFirstByUserIdAndConversationIdOrderByUpdatedAtDesc(userId, conversationId);
        if (entity.isPresent()) {
            try {
                LearningPlan plan = objectMapper.readValue(entity.get().getPlanJson(), LearningPlan.class);
                return ApiResponse.success("ok", plan);
            } catch (Exception e) {
                log.warn("解析计划 JSON 失败: {}", e.getMessage());
            }
        }
        return ApiResponse.success("ok", null);
    }

    /** 读取当前计划版本中各任务的真实完成状态。 */
    @GetMapping("/plan/task-statuses")
    public ApiResponse<List<Map<String, Object>>> getTaskStatuses(
            @RequestParam(required = false) Long userId,
            Authentication authentication,
            @RequestParam String conversationId) {
        userId = CurrentUser.id(authentication);
        Optional<LearningPlanEntity> currentPlan = planVersionService.getCurrentEntity(userId, conversationId);
        if (currentPlan.isEmpty()) {
            return ApiResponse.success("ok", List.of());
        }
        List<Map<String, Object>> statuses = taskRepository
                .findByUserIdAndPlanId(userId, currentPlan.get().getId())
                .stream()
                .filter(task -> task.getWeekNumber() != null && task.getTaskIndex() != null)
                .map(task -> Map.<String, Object>of(
                        "weekNumber", task.getWeekNumber(),
                        "taskIndex", task.getTaskIndex(),
                        "status", task.getStatus()))
                .toList();
        return ApiResponse.success("ok", statuses);
    }

    /** 保存用户编辑后的计划 */
    @PutMapping("/plan")
    public ApiResponse<LearningPlan> savePlan(@RequestBody Map<String, Object> body,
                                              Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        String conversationId = String.valueOf(body.getOrDefault("conversationId", "")).trim();
        Object planData = body.get("plan");

        if (!(planData instanceof Map<?, ?>) || conversationId.isBlank()) {
            return ApiResponse.error(400, "缺少 plan 数据或 conversationId");
        }

        try {
            LearningPlan plan = objectMapper.convertValue(planData, LearningPlan.class);
            planVersionService.saveNewVersion(userId, conversationId, plan,
                    "manual_edit", "用户在计划编辑器中保存修改");
            return ApiResponse.success("计划已保存", plan);
        } catch (Exception e) {
            log.error("保存计划失败: {}", e.getMessage());
            return ApiResponse.error(500, "保存失败");
        }
    }

    /** 获取对话内计划版本历史，便于回溯智能调整。 */
    @GetMapping("/plan/history")
    public ApiResponse<List<LearningPlanEntity>> getPlanHistory(
            @RequestParam(required = false) Long userId,
            Authentication authentication,
            @RequestParam String conversationId) {
        userId = CurrentUser.id(authentication);
        return ApiResponse.success("ok", planVersionService.getHistory(userId, conversationId));
    }
}
