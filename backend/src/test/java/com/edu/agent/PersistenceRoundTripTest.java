package com.edu.agent;

import com.edu.agent.model.AiEvaluation;
import com.edu.agent.model.Conversation;
import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.ProfileEvidence;
import com.edu.agent.model.TaskSubmission;
import com.edu.agent.model.UploadedFileRecord;
import com.edu.agent.repository.AiEvaluationRepository;
import com.edu.agent.repository.ConversationRepository;
import com.edu.agent.repository.TaskSubmissionRepository;
import com.edu.agent.repository.UploadedFileRecordRepository;
import com.edu.agent.service.ConversationSessionService;
import com.edu.agent.service.LearningPlanVersionService;
import com.edu.agent.service.ProfileEvidenceService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:persistence-round-trip;MODE=MYSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ConversationSessionService.class, ProfileEvidenceService.class, LearningPlanVersionService.class})
class PersistenceRoundTripTest {

    @Autowired private EntityManager entityManager;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private UploadedFileRecordRepository uploadedFileRecordRepository;
    @Autowired private TaskSubmissionRepository submissionRepository;
    @Autowired private AiEvaluationRepository evaluationRepository;
    @Autowired private ConversationSessionService conversationSessionService;
    @Autowired private ProfileEvidenceService profileEvidenceService;
    @Autowired private LearningPlanVersionService planVersionService;

    @Test
    void conversationTitleAttachmentAndFileHistorySurviveDatabaseRoundTrip() {
        long userId = 101L;
        String conversationId = "persistence-conversation";
        conversationSessionService.saveTitle(userId, conversationId, "Java 并发学习");
        conversationSessionService.updateIntelligence(
                userId, conversationId, "目标：掌握并发基础", "{\"energy\":\"low\"}",
                "STUDY_QA", "ANSWERING", "", "{\"score\":95}");

        Conversation message = new Conversation();
        message.setUserId(userId);
        message.setConversationId(conversationId);
        message.setRole("user");
        message.setContent("请分析这张图");
        message.setAiContent("请分析这张图（完整 AI 上下文）");
        message.setAttachmentName("diagram.png");
        message.setAttachmentType("image");
        message.setAttachmentData("data:image/png;base64,AA==");
        message.setTimestamp(LocalDateTime.now());
        Long messageId = conversationRepository.saveAndFlush(message).getId();

        UploadedFileRecord file = new UploadedFileRecord();
        file.setUserId(userId);
        file.setConversationId(conversationId);
        file.setFileName("notes.pdf");
        file.setSizeBytes(2048L);
        file.setPurpose("CHAT");
        uploadedFileRecordRepository.saveAndFlush(file);

        entityManager.clear();

        Conversation restored = conversationRepository.findById(messageId).orElseThrow();
        assertThat(restored.getContent()).isEqualTo("请分析这张图");
        assertThat(restored.getAiContent()).contains("完整 AI 上下文");
        assertThat(restored.getAttachmentData()).isEqualTo("data:image/png;base64,AA==");
        assertThat(conversationSessionService.getTitleMap(userId))
                .containsEntry(conversationId, "Java 并发学习");
        assertThat(conversationSessionService.findSession(userId, conversationId).orElseThrow())
                .satisfies(session -> {
                    assertThat(session.getMemorySummary()).contains("掌握并发");
                    assertThat(session.getTemporaryStateJson()).contains("energy");
                    assertThat(session.getLastIntent()).isEqualTo("STUDY_QA");
                });
        assertThat(uploadedFileRecordRepository.findByUserIdOrderByUploadedAtDesc(
                userId, PageRequest.of(0, 10)))
                .singleElement()
                .extracting(UploadedFileRecord::getFileName)
                .isEqualTo("notes.pdf");
    }

    @Test
    void submissionEvaluationAndConversationBindingSurviveDatabaseRoundTrip() {
        TaskSubmission submission = new TaskSubmission();
        submission.setTaskId(501L);
        submission.setUserId(102L);
        submission.setConversationId("conversation-102");
        submission.setFileName("result.docx");
        submission.setFileSize(4096L);
        submission.setContent("阶段性学习成果");
        submission.setStatus(TaskSubmission.STATUS_EVALUATED);
        Long submissionId = submissionRepository.saveAndFlush(submission).getId();

        AiEvaluation evaluation = new AiEvaluation();
        evaluation.setSubmissionId(submissionId);
        evaluation.setScore(92);
        evaluation.setAnalysis("内容完整");
        evaluation.setSuggestion("补充更多实践案例");
        evaluationRepository.saveAndFlush(evaluation);

        entityManager.clear();

        TaskSubmission restored = submissionRepository
                .findByUserIdAndConversationIdOrderBySubmissionTimeDesc(102L, "conversation-102")
                .get(0);
        assertThat(restored.getFileName()).isEqualTo("result.docx");
        assertThat(restored.getStatus()).isEqualTo(TaskSubmission.STATUS_EVALUATED);
        assertThat(evaluationRepository.findBySubmissionId(restored.getId()))
                .get()
                .extracting(AiEvaluation::getScore)
                .isEqualTo(92);
    }

    @Test
    void profileEvidenceRejectsWeakerConflictAndPlanKeepsVersions() {
        long userId = 103L;
        String conversationId = "intelligence-103";
        Set<String> firstAccepted = profileEvidenceService.persist(
                userId, conversationId, 1L,
                "[{\"dimension\":\"shortTermGoal\",\"value\":\"通过Java考试\","
                        + "\"evidence\":\"我的目标是通过Java考试\",\"confidence\":0.95,"
                        + "\"scope\":\"LONG_TERM\",\"action\":\"replace\"}]");
        Set<String> weakerAccepted = profileEvidenceService.persist(
                userId, conversationId, 2L,
                "[{\"dimension\":\"shortTermGoal\",\"value\":\"学习绘画\","
                        + "\"evidence\":\"也许以后学绘画\",\"confidence\":0.65,"
                        + "\"scope\":\"LONG_TERM\",\"action\":\"replace\"}]");

        assertThat(firstAccepted).containsExactly("shortTermGoal");
        assertThat(weakerAccepted).isEmpty();
        List<ProfileEvidence> evidence = profileEvidenceService.getEvidence(userId, conversationId);
        assertThat(evidence).extracting(ProfileEvidence::getStatus)
                .containsExactly("CONFLICT", "ACTIVE");

        LearningPlan first = new LearningPlan();
        first.setWeeks(List.of());
        LearningPlan second = new LearningPlan();
        second.setWeeks(List.of());
        planVersionService.saveNewVersion(userId, conversationId, first, "create", "首次生成");
        planVersionService.saveNewVersion(userId, conversationId, second, "modify_week", "调整第三周");

        assertThat(planVersionService.getHistory(userId, conversationId))
                .extracting(entity -> entity.getVersionNumber())
                .containsExactly(2, 1);
        assertThat(planVersionService.getHistory(userId, conversationId).get(0).getParentPlanId())
                .isNotNull();
    }
}
