package com.edu.agent.service;

import com.edu.agent.model.TaskSubmission;
import com.edu.agent.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 以用户和对话双重条件删除一个对话及其全部对话级数据。 */
@Service
public class ConversationDeletionService {

    private final ConversationRepository conversationRepository;
    private final UserProfileRepository userProfileRepository;
    private final LearningPlanRepository learningPlanRepository;
    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final AiEvaluationRepository aiEvaluationRepository;
    private final ConversationSessionRepository conversationSessionRepository;
    private final UploadedFileRecordRepository uploadedFileRecordRepository;
    private final ProfileEvidenceRepository profileEvidenceRepository;
    private final AgentDecisionRecordRepository agentDecisionRecordRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;
    private final PracticeQuestionRepository practiceQuestionRepository;

    public ConversationDeletionService(ConversationRepository conversationRepository,
                                       UserProfileRepository userProfileRepository,
                                       LearningPlanRepository learningPlanRepository,
                                       TaskRepository taskRepository,
                                       TaskSubmissionRepository taskSubmissionRepository,
                                       AiEvaluationRepository aiEvaluationRepository,
                                       ConversationSessionRepository conversationSessionRepository,
                                       UploadedFileRecordRepository uploadedFileRecordRepository,
                                       ProfileEvidenceRepository profileEvidenceRepository,
                                       AgentDecisionRecordRepository agentDecisionRecordRepository,
                                       KnowledgeDocumentRepository knowledgeDocumentRepository,
                                       KnowledgeChunkRepository knowledgeChunkRepository,
                                       PracticeQuestionRepository practiceQuestionRepository) {
        this.conversationRepository = conversationRepository;
        this.userProfileRepository = userProfileRepository;
        this.learningPlanRepository = learningPlanRepository;
        this.taskRepository = taskRepository;
        this.taskSubmissionRepository = taskSubmissionRepository;
        this.aiEvaluationRepository = aiEvaluationRepository;
        this.conversationSessionRepository = conversationSessionRepository;
        this.uploadedFileRecordRepository = uploadedFileRecordRepository;
        this.profileEvidenceRepository = profileEvidenceRepository;
        this.agentDecisionRecordRepository = agentDecisionRecordRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeChunkRepository = knowledgeChunkRepository;
        this.practiceQuestionRepository = practiceQuestionRepository;
    }

    @Transactional
    public boolean delete(Long userId, String conversationId) {
        if (!conversationRepository.existsByUserIdAndConversationId(userId, conversationId)) {
            return false;
        }

        List<TaskSubmission> submissions = taskSubmissionRepository
                .findByUserIdAndConversationIdOrderBySubmissionTimeDesc(userId, conversationId);
        submissions.forEach(submission -> aiEvaluationRepository.deleteBySubmissionId(submission.getId()));
        taskSubmissionRepository.deleteByUserIdAndConversationId(userId, conversationId);
        taskRepository.deleteByUserIdAndConversationId(userId, conversationId);
        learningPlanRepository.deleteByUserIdAndConversationId(userId, conversationId);

        profileEvidenceRepository.deleteByUserIdAndConversationId(userId, conversationId);
        agentDecisionRecordRepository.deleteByUserIdAndConversationId(userId, conversationId);
        userProfileRepository.deleteByUserIdAndConversationId(userId, conversationId);
        uploadedFileRecordRepository.deleteByUserIdAndConversationId(userId, conversationId);
        practiceQuestionRepository.deleteByUserIdAndConversationId(userId, conversationId);

        knowledgeChunkRepository.deleteByUserIdAndConversationId(userId, conversationId);
        knowledgeDocumentRepository.deleteByUserIdAndConversationId(userId, conversationId);
        conversationSessionRepository.deleteByUserIdAndConversationId(userId, conversationId);
        conversationRepository.deleteByUserIdAndConversationId(userId, conversationId);
        return true;
    }
}
