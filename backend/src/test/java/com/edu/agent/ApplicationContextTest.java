package com.edu.agent;

import com.edu.agent.controller.dto.SubmissionDetail;
import com.edu.agent.model.LearningPlan;
import com.edu.agent.service.ConversationSessionService;
import com.edu.agent.service.LearningPlanVersionService;
import com.edu.agent.service.ProfileService;
import com.edu.agent.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:application-context;MODE=MYSQL;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "ai.mock-enabled=true"
        })
class ApplicationContextTest {

    @Autowired private LearningPlanVersionService planVersionService;
    @Autowired private SubmissionService submissionService;
    @Autowired private ConversationSessionService conversationSessionService;
    @Autowired private ProfileService profileService;

    @Test
    void contextLoadsWithIntelligencePipeline() {
        // 构造器注入、Repository 查询派生和实体映射由上下文启动统一验证。
    }

    @Test
    void mockSubmissionFeedsEvaluationBackIntoMemoryAndProfile() throws InterruptedException {
        long userId = 9901L;
        String conversationId = "submission-loop";
        LearningPlan.PlanWeek week = new LearningPlan.PlanWeek();
        week.setWeekNumber(1);
        week.setTopic("并发实践");
        week.setTasks(List.of("完成线程池实验"));
        week.setResources(List.of());
        LearningPlan plan = new LearningPlan();
        plan.setWeeks(List.of(week));
        planVersionService.saveNewVersion(userId, conversationId, plan, "create", "测试计划");

        Long submissionId = submissionService.submit(
                userId, conversationId, "result.txt", 128L, 1, 0, "线程池实验结果");
        SubmissionDetail detail = null;
        for (int i = 0; i < 40; i++) {
            detail = submissionService.getSubmissionDetail(submissionId, userId);
            if ("EVALUATED".equals(detail.getStatus())) break;
            Thread.sleep(50);
        }

        assertThat(detail).isNotNull();
        assertThat(detail.getStatus()).isEqualTo("EVALUATED");
        assertThat(detail.getEvaluation().getWeaknessesJson()).contains("实践验证不足");
        assertThat(conversationSessionService.findSession(userId, conversationId).orElseThrow()
                .getTemporaryStateJson()).contains("lastSubmissionScore");
        assertThat(profileService.getCurrentProfile(userId, conversationId).getWeaknessPoints())
                .contains("实践验证不足");

        Long secondSubmissionId = submissionService.submit(
                userId, conversationId, "result-v2.txt", 160L, 1, 0,
                "线程池实验第二版：补充了并发数量、运行耗时和验证结果");
        SubmissionDetail secondDetail = null;
        for (int i = 0; i < 40; i++) {
            secondDetail = submissionService.getSubmissionDetail(secondSubmissionId, userId);
            if ("EVALUATED".equals(secondDetail.getStatus())) break;
            Thread.sleep(50);
        }
        assertThat(secondDetail).isNotNull();
        assertThat(secondDetail.getVersionNumber()).isEqualTo(2);
        assertThat(secondDetail.getPreviousSubmissionId()).isEqualTo(submissionId);
        assertThat(secondDetail.getComparisonSubmissionId()).isEqualTo(submissionId);
        assertThat(secondDetail.getEvaluation().getScoreDelta()).isEqualTo(4);
        assertThat(secondDetail.getEvaluation().getGrowthOutcome()).isEqualTo("PROGRESSED");
    }
}
