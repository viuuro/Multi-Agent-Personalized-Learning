package com.edu.agent;

import com.edu.agent.controller.PlanController;
import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.LearningPlan;
import com.edu.agent.repository.LearningPlanRepository;
import com.edu.agent.service.AgentOrchestrationService;
import com.edu.agent.service.LearningPlanVersionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PlanControllerTest {

    private PlanController controller;

    @BeforeEach
    void setUp() {
        controller = new PlanController(
                mock(AgentOrchestrationService.class),
                mock(LearningPlanRepository.class),
                mock(LearningPlanVersionService.class),
                new ObjectMapper());
    }

    @Test
    void reportsMissingUserIdAsClientInputError() {
        assertThatThrownBy(() -> controller.generatePlan(Map.of("conversationId", "conversation-1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("缺少 userId");
    }

    @Test
    void rejectsMalformedPlanBeforePersistence() {
        ApiResponse<LearningPlan> response = controller.savePlan(Map.of(
                "userId", 1,
                "conversationId", "conversation-1",
                "plan", "not-an-object"));

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).contains("plan");
    }
}
