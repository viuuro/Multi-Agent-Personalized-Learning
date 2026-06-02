package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.LearningPlan;
import com.edu.agent.service.AgentOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 计划控制器 —— 触发生成 4 周学习计划
 *
 * POST /api/plan 调用 AgentOrchestrationService，
 * 内部自动判断 Mock/真实模式（Python AI → MiMo-v2.5 API）
 */
@RestController
@RequestMapping("/api")
public class PlanController {

    private static final Logger log = LoggerFactory.getLogger(PlanController.class);

    private final AgentOrchestrationService orchestrationService;

    public PlanController(AgentOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @PostMapping("/plan")
    public ApiResponse<LearningPlan> generatePlan(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        log.info(">>> POST /api/plan —— 触发多智能体协同, userId={}", userId);
        LearningPlan plan = orchestrationService.generatePlan(userId);
        log.info(">>> 计划生成完成。共 {} 周。", plan.getWeeks().size());
        return ApiResponse.success("多智能体协同完成，学习计划已生成", plan);
    }
}
