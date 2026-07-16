package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.LearningPlanEntity;
import com.edu.agent.repository.LearningPlanRepository;
import com.edu.agent.service.AgentOrchestrationService;
import com.edu.agent.service.LearningPlanVersionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

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
    private final LearningPlanVersionService planVersionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlanController(AgentOrchestrationService orchestrationService,
                          LearningPlanRepository planRepository,
                          LearningPlanVersionService planVersionService) {
        this.orchestrationService = orchestrationService;
        this.planRepository = planRepository;
        this.planVersionService = planVersionService;
    }

    /** 生成计划并持久化 */
    @PostMapping("/plan")
    public ApiResponse<LearningPlan> generatePlan(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
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
    public ApiResponse<LearningPlan> getPlan(@RequestParam Long userId,
                                             @RequestParam String conversationId) {
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

    /** 保存用户编辑后的计划 */
    @PutMapping("/plan")
    public ApiResponse<LearningPlan> savePlan(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String conversationId = String.valueOf(body.getOrDefault("conversationId", "")).trim();
        @SuppressWarnings("unchecked")
        Map<String, Object> planData = (Map<String, Object>) body.get("plan");

        if (planData == null || conversationId.isBlank()) {
            return ApiResponse.error(400, "缺少 plan 数据或 conversationId");
        }

        try {
            String planJson = objectMapper.writeValueAsString(planData);
            LearningPlan plan = objectMapper.readValue(planJson, LearningPlan.class);
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
            @RequestParam Long userId,
            @RequestParam String conversationId) {
        return ApiResponse.success("ok", planVersionService.getHistory(userId, conversationId));
    }
}
