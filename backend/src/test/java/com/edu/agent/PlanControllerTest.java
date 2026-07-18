package com.edu.agent;

import com.edu.agent.controller.PlanController;
import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.LearningPlan;
import com.edu.agent.repository.LearningPlanRepository;
import com.edu.agent.service.AgentOrchestrationService;
import com.edu.agent.service.LearningPlanVersionService;
import com.edu.agent.service.RequestRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.security.core.Authentication;

class PlanControllerTest {

    private PlanController controller;

    @BeforeEach
    void setUp() {
        controller = new PlanController(
                mock(AgentOrchestrationService.class),
                mock(LearningPlanRepository.class),
                mock(LearningPlanVersionService.class),
                new RequestRateLimiter(),
                new ObjectMapper());
    }

    private Authentication authenticatedUser() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("1");
        return authentication;
    }

    @Test
    void rejectsMalformedPlanBeforePersistence() {
        ApiResponse<LearningPlan> response = controller.savePlan(Map.of(
                "conversationId", "conversation-1",
                "plan", "not-an-object"), authenticatedUser());

        assertThat(response.getCode()).isEqualTo(400);
        assertThat(response.getMessage()).contains("plan");
    }
}
