package com.edu.agent;

import com.edu.agent.model.*;
import com.edu.agent.repository.*;
import com.edu.agent.service.ConversationDeletionService;
import com.edu.agent.service.ConversationSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:conversation-delete;MODE=MYSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({ConversationDeletionService.class, ConversationSessionService.class})
class ConversationDeletionServiceTest {

    @Autowired private ConversationDeletionService deletionService;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private ConversationSessionRepository sessionRepository;
    @Autowired private UserProfileRepository profileRepository;
    @Autowired private LearningPlanRepository planRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskSubmissionRepository submissionRepository;
    @Autowired private AiEvaluationRepository evaluationRepository;
    @Autowired private UploadedFileRecordRepository fileRepository;
    @Autowired private KnowledgeDocumentRepository documentRepository;
    @Autowired private KnowledgeChunkRepository chunkRepository;
    @Autowired private PracticeQuestionRepository practiceQuestionRepository;
    @Autowired private ConversationSessionService sessionService;

    @Test
    void deletesOnlyTheRequestedConversationWorkspace() {
        long userId = 201L;
        String targetId = "conversation-to-delete";
        String survivorId = "conversation-to-keep";

        conversationRepository.save(message(userId, targetId, "删除我"));
        conversationRepository.save(message(userId, survivorId, "保留我"));
        sessionService.saveTitle(userId, targetId, "待删除对话");
        sessionService.saveTitle(userId, survivorId, "保留对话");

        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setConversationId(targetId);
        profile.setProfileJson("{}");
        profile.setUpdatedAt(LocalDateTime.now());
        profileRepository.save(profile);

        LearningPlanEntity plan = new LearningPlanEntity();
        plan.setUserId(userId);
        plan.setConversationId(targetId);
        plan.setPlanJson("{\"weeks\":[]}");
        plan = planRepository.saveAndFlush(plan);

        Task task = new Task();
        task.setUserId(userId);
        task.setConversationId(targetId);
        task.setPlanId(plan.getId());
        task.setWeekNumber(1);
        task.setTaskIndex(0);
        task.setDescription("完成练习");
        task = taskRepository.saveAndFlush(task);

        TaskSubmission submission = new TaskSubmission();
        submission.setUserId(userId);
        submission.setConversationId(targetId);
        submission.setTaskId(task.getId());
        submission.setContent("学习成果");
        submission = submissionRepository.saveAndFlush(submission);

        AiEvaluation evaluation = new AiEvaluation();
        evaluation.setSubmissionId(submission.getId());
        evaluation.setScore(88);
        evaluation = evaluationRepository.saveAndFlush(evaluation);

        UploadedFileRecord file = new UploadedFileRecord();
        file.setUserId(userId);
        file.setConversationId(targetId);
        file.setFileName("result.md");
        file.setSizeBytes(128L);
        file.setPurpose("SUBMISSION");
        fileRepository.saveAndFlush(file);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setUserId(userId);
        document.setConversationId(targetId);
        document.setScope(KnowledgeDocument.SCOPE_CONVERSATION);
        document.setSourceType("CHAT_UPLOAD");
        document.setTitle("对话资料");
        document.setChecksum("delete-test-checksum");
        document = documentRepository.saveAndFlush(document);

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocumentId(document.getId());
        chunk.setUserId(userId);
        chunk.setConversationId(targetId);
        chunk.setScope(KnowledgeDocument.SCOPE_CONVERSATION);
        chunk.setChunkIndex(0);
        chunk.setContent("对话独立知识");
        chunk.setDocumentTitle("对话资料");
        chunk = chunkRepository.saveAndFlush(chunk);

        PracticeQuestion question = new PracticeQuestion();
        question.setUserId(userId);
        question.setConversationId(targetId);
        question.setBatchId("delete-batch");
        question.setWeekNumber(1);
        question.setTaskIndex(0);
        question.setWeekTopic("练习主题");
        question.setTaskTitle("练习任务");
        question.setQuestionType("TRUE_FALSE");
        question.setDifficulty("EASY");
        question.setQuestionText("测试题目");
        question.setOptionsJson("[\"正确\",\"错误\"]");
        question.setCorrectAnswer("A");
        question = practiceQuestionRepository.saveAndFlush(question);

        assertThat(deletionService.delete(userId, targetId)).isTrue();

        assertThat(conversationRepository.existsByUserIdAndConversationId(userId, targetId)).isFalse();
        assertThat(sessionRepository.findByUserIdAndConversationId(userId, targetId)).isEmpty();
        assertThat(profileRepository.findFirstByUserIdAndConversationIdOrderByUpdatedAtDescIdDesc(userId, targetId)).isEmpty();
        assertThat(planRepository.findFirstByUserIdAndConversationIdOrderByUpdatedAtDesc(userId, targetId)).isEmpty();
        assertThat(submissionRepository.findById(submission.getId())).isEmpty();
        assertThat(evaluationRepository.findById(evaluation.getId())).isEmpty();
        assertThat(documentRepository.findById(document.getId())).isEmpty();
        assertThat(chunkRepository.findById(chunk.getId())).isEmpty();
        assertThat(practiceQuestionRepository.findById(question.getId())).isEmpty();

        assertThat(conversationRepository.existsByUserIdAndConversationId(userId, survivorId)).isTrue();
        assertThat(sessionRepository.findByUserIdAndConversationId(userId, survivorId)).isPresent();
        assertThat(deletionService.delete(userId, "missing-conversation")).isFalse();
    }

    private Conversation message(long userId, String conversationId, String content) {
        Conversation message = new Conversation();
        message.setUserId(userId);
        message.setConversationId(conversationId);
        message.setRole("user");
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        return message;
    }
}
