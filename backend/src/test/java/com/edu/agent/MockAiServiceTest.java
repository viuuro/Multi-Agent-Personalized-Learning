package com.edu.agent;

import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.UserProfile;
import com.edu.agent.service.MockAiService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiServiceTest {

    private final MockAiService service = new MockAiService();

    @Test
    void placeholderInterestFallsBackToUsefulConcretePlanResources() {
        UserProfile profile = profile("待评估");

        LearningPlan plan = service.mockPlanGeneration(profile);

        assertThat(plan.getWeeks()).hasSize(4);
        assertThat(plan.getWeeks().get(0).getTopic())
                .isEqualTo("软件工程基础入门")
                .doesNotContain("基础基础");
        assertThat(plan.getWeeks())
                .allSatisfy(week -> {
                    assertThat(week.getTopic()).doesNotContain("待评估");
                    assertThat(week.getResources()).hasSize(2);
                    assertThat(week.getResources()).allSatisfy(resource -> {
                        assertThat(resource.getTitle()).doesNotContain("待评估");
                        assertThat(resource.getUrl()).startsWith("https://");
                        assertThat(resource.getUrl().toLowerCase())
                                .doesNotContain("/search", "search?", "search.htm", "?q=");
                    });
                });
    }

    @Test
    void bilibiliFallbackIsAlwaysADirectBvVideo() {
        LearningPlan plan = service.mockPlanGeneration(profile("Java"));

        assertThat(plan.getWeeks().get(0).getResources())
                .filteredOn(resource -> "B站".equals(resource.getPlatform()))
                .singleElement()
                .extracting(LearningPlan.Resource::getUrl)
                .asString()
                .matches("https://www\\.bilibili\\.com/video/BV[0-9A-Za-z]{10}");
    }

    private UserProfile profile(String interest) {
        UserProfile profile = new UserProfile();
        profile.setInterestAreas(List.of(interest));
        profile.setWeaknessPoints(List.of("待评估"));
        profile.setShortTermGoal("探索学习方向");
        profile.setKnowledgeBase(5);
        profile.setLearningPace(5);
        profile.setCognitiveStyle("visual");
        return profile;
    }
}
