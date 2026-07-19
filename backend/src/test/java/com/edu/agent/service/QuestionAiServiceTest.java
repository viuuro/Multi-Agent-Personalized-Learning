package com.edu.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionAiServiceTest {

    private final QuestionAiService service = new QuestionAiService(new ObjectMapper(), true);

    @Test
    void detectsAQuestionCopiedIntoItsOwnOption() {
        assertThat(service.hasQuestionOptionConflict(
                "下列哪一项完整描述了数据库事务的原子性？",
                List.of("下列哪一项完整描述了数据库事务的原子性", "一致性", "隔离性", "持久性")))
                .isTrue();
        assertThat(service.containsOpaqueSourceReference("根据资料3可知，该结论成立")).isTrue();
        assertThat(service.containsOpaqueSourceReference("根据题干给出的事务定义判断")).isFalse();
    }

    @Test
    void detectsReorderedOptionSetsAcrossDifferentQuestions() {
        QuestionAiService.GeneratedQuestion first = question(
                "哪项实践最能验证链表插入操作？",
                List.of("运行边界用例", "背诵定义", "忽略输出", "复制答案"));
        QuestionAiService.GeneratedQuestion second = question(
                "学习数据库事务时应该采用哪种方式？",
                List.of("复制答案", "运行边界用例", "忽略输出", "背诵定义"));

        assertThat(service.isDuplicateQuestion(first, second)).isTrue();
    }

    @Test
    void fallbackChoiceQuestionsUseIndependentStemsAndOptionSets() {
        List<QuestionAiService.GeneratedQuestion> questions = service.generate(
                "{}", "数据结构", "理解链表插入", "SINGLE_CHOICE", "MEDIUM", 10);

        assertThat(questions).hasSize(10);
        assertThat(questions.get(0).question()).contains("顺序表").doesNotContain("学习证据");
        assertThat(questions.get(1).question()).contains("单链表").doesNotContain("学习证据");
        for (int left = 0; left < questions.size(); left++) {
            assertThat(service.hasQuestionOptionConflict(
                    questions.get(left).question(), questions.get(left).options())).isFalse();
            for (int right = left + 1; right < questions.size(); right++) {
                assertThat(service.isDuplicateQuestion(questions.get(left), questions.get(right))).isFalse();
            }
        }
    }

    @Test
    void computerOrganizationFallbackUsesComputableProfessionalQuestions() {
        List<QuestionAiService.GeneratedQuestion> questions = service.generate(
                "{}", "计算机组成原理", "补码与 Cache 地址映射", "SINGLE_CHOICE", "MEDIUM", 3);

        assertThat(questions).hasSize(3);
        assertThat(questions.get(0).question()).contains("8 位补码");
        assertThat(questions.get(1).question()).contains("直接映射 Cache");
        assertThat(questions.subList(0, 2)).allSatisfy(question ->
                assertThat(question.explanation()).hasSizeGreaterThan(25));
    }

    @Test
    void subsequentBatchAvoidsQuestionsAndOptionsFromEarlierBatch() {
        List<QuestionAiService.GeneratedQuestion> first = service.generate(
                "{}", "数据结构", "理解链表插入", "SINGLE_CHOICE", "MEDIUM", 5);
        List<QuestionAiService.GeneratedQuestion> second = service.generate(
                "{}", "数据结构", "理解链表插入", "SINGLE_CHOICE", "MEDIUM", 5,
                "", List.of(), first);

        assertThat(second).hasSize(5);
        assertThat(second).allSatisfy(candidate -> assertThat(first)
                .noneMatch(existing -> service.isDuplicateQuestion(existing, candidate)));

        List<QuestionAiService.GeneratedQuestion> third = service.generate(
                "{}", "数据结构", "理解链表插入", "SINGLE_CHOICE", "MEDIUM", 5,
                "", List.of(), java.util.stream.Stream.concat(first.stream(), second.stream()).toList());
        assertThat(third).hasSize(5);
        assertThat(third).allSatisfy(candidate -> {
            assertThat(first).noneMatch(existing -> service.isDuplicateQuestion(existing, candidate));
            assertThat(second).noneMatch(existing -> service.isDuplicateQuestion(existing, candidate));
        });
    }

    @Test
    void localFallbackKeepsProducingAcrossManyHistoricalBatches() {
        List<QuestionAiService.GeneratedQuestion> history = new java.util.ArrayList<>();
        for (int batch = 0; batch < 8; batch++) {
            List<QuestionAiService.GeneratedQuestion> generated = service.generate(
                    "{}", "数据结构", "理解链表插入", "SINGLE_CHOICE", "MEDIUM", 5,
                    "", List.of(), history);
            assertThat(generated).as("batch %s", batch + 1).hasSize(5);
            history.addAll(generated);
        }
    }

    private QuestionAiService.GeneratedQuestion question(String text, List<String> options) {
        return new QuestionAiService.GeneratedQuestion(
                text, options, "A", "解析内容足够完整", "知识点", "学习目标", "应用", List.of(), 80);
    }
}
