package com.edu.agent.service;

import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.LearningPlanEntity;
import com.edu.agent.repository.LearningPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** 以追加版本而非覆盖方式保存学习计划。 */
@Service
public class LearningPlanVersionService {

    private final LearningPlanRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LearningPlanVersionService(LearningPlanRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<LearningPlanEntity> getCurrentEntity(Long userId, String conversationId) {
        return repository.findFirstByUserIdAndConversationIdOrderByUpdatedAtDesc(userId, conversationId);
    }

    @Transactional
    public LearningPlanEntity saveNewVersion(Long userId,
                                             String conversationId,
                                             LearningPlan plan,
                                             String revisionType,
                                             String revisionReason) {
        try {
            LearningPlanEntity previous = getCurrentEntity(userId, conversationId).orElse(null);
            LearningPlanEntity entity = new LearningPlanEntity();
            entity.setUserId(userId);
            entity.setConversationId(conversationId);
            entity.setPlanJson(objectMapper.writeValueAsString(plan));
            entity.setVersionNumber(previous == null || previous.getVersionNumber() == null
                    ? 1 : previous.getVersionNumber() + 1);
            entity.setParentPlanId(previous == null ? null : previous.getId());
            entity.setRevisionType(normalize(revisionType, "create", 40));
            entity.setRevisionReason(normalize(revisionReason, "", 1000));
            return repository.save(entity);
        } catch (Exception e) {
            throw new IllegalStateException("学习计划版本保存失败", e);
        }
    }

    @Transactional(readOnly = true)
    public List<LearningPlanEntity> getHistory(Long userId, String conversationId) {
        return repository.findByUserIdAndConversationIdOrderByVersionNumberDesc(userId, conversationId);
    }

    private String normalize(String value, String fallback, int maxLength) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
