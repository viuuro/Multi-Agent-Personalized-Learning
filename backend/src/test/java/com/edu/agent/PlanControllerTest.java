package com.edu.agent;

import com.edu.agent.controller.PlanController;
import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.LearningPlanEntity;
import com.edu.agent.model.Task;
import com.edu.agent.repository.LearningPlanRepository;
import com.edu.agent.repository.TaskRepository;
import com.edu.agent.service.AgentOrchestrationService;
import com.edu.agent.service.LearningPlanVersionService;
import com.edu.agent.service.RequestRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.security.core.Authentication;

class PlanControllerTest {

    private PlanController controller;
    private TaskRepository taskRepository;
    private LearningPlanVersionService planVersionService;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        planVersionService = mock(LearningPlanVersionService.class);
        controller = new PlanController(
                mock(AgentOrchestrationService.class),
                mock(LearningPlanRepository.class),
                taskRepository,
                planVersionService,
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

    @Test
    void returnsStatusesOnlyForTasksInCurrentPlanVersion() {
        LearningPlanEntity currentPlan = new LearningPlanEntity();
        currentPlan.setId(12L);
        Task completed = new Task();
        completed.setWeekNumber(1);
        completed.setTaskIndex(0);
        completed.setStatus("COMPLETED");
        when(planVersionService.getCurrentEntity(1L, "conversation-1"))
                .thenReturn(Optional.of(currentPlan));
        when(taskRepository.findByUserIdAndPlanId(1L, 12L)).thenReturn(List.of(completed));

        ApiResponse<List<Map<String, Object>>> response = controller.getTaskStatuses(
                99L, authenticatedUser(), "conversation-1");

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getData()).containsExactly(Map.of(
                "weekNumber", 1,
                "taskIndex", 0,
                "status", "COMPLETED"));
    }
}
