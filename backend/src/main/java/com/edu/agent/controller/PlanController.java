package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.LearningPlanEntity;
import com.edu.agent.repository.LearningPlanRepository;
import com.edu.agent.service.AgentOrchestrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlanController(AgentOrchestrationService orchestrationService,
                          LearningPlanRepository planRepository) {
        this.orchestrationService = orchestrationService;
        this.planRepository = planRepository;
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

        LearningPlan plan = orchestrationService.generatePlan(userId, conversationId);
        log.info(">>> 计划生成完成。共 {} 周。", plan.getWeeks().size());

        // 持久化到数据库
        savePlanToDb(userId, conversationId, plan);

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
            LearningPlanEntity entity = planRepository
                    .findFirstByUserIdAndConversationIdOrderByUpdatedAtDesc(userId, conversationId)
                    .orElse(new LearningPlanEntity());
            entity.setUserId(userId);
            entity.setConversationId(conversationId);
            entity.setPlanJson(planJson);
            planRepository.save(entity);

            LearningPlan plan = objectMapper.readValue(planJson, LearningPlan.class);
            return ApiResponse.success("计划已保存", plan);
        } catch (Exception e) {
            log.error("保存计划失败: {}", e.getMessage());
            return ApiResponse.error(500, "保存失败");
        }
    }

    /** 将计划存入数据库（生成后自动调用） */
    private void savePlanToDb(Long userId, String conversationId, LearningPlan plan) {
        try {
            String planJson = objectMapper.writeValueAsString(plan);
            LearningPlanEntity entity = planRepository
                    .findFirstByUserIdAndConversationIdOrderByUpdatedAtDesc(userId, conversationId)
                    .orElse(new LearningPlanEntity());
            entity.setUserId(userId);
            entity.setConversationId(conversationId);
            entity.setPlanJson(planJson);
            planRepository.save(entity);
            log.info(">>> 计划已持久化到 MySQL, userId={}", userId);
        } catch (Exception e) {
            log.warn("计划持久化失败（不影响返回）: {}", e.getMessage());
        }
    }
}
