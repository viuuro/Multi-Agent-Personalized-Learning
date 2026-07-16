package com.edu.agent.service;

import com.edu.agent.model.AgentDecisionRecord;
import com.edu.agent.repository.AgentDecisionRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 持久化每轮路由与质量信息，供回归分析和调试。 */
@Service
public class AgentDecisionService {

    private final AgentDecisionRecordRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentDecisionService(AgentDecisionRecordRepository repository) {
        this.repository = repository;
    }

    public void record(Long userId, String conversationId, Long messageId, Map<String, Object> result) {
        if (userId == null) return;
        AgentDecisionRecord record = new AgentDecisionRecord();
        record.setUserId(userId);
        record.setConversationId(conversationId);
        record.setMessageId(messageId);
        record.setIntent(text(result, "intent", "STUDY_QA"));
        record.setDialogueState(text(result, "dialogue_state", "ANSWERING"));
        record.setPlanAction(text(result, "plan_action", "none"));
        record.setClarificationRequired(Boolean.parseBoolean(text(result, "needs_clarification", "false")));
        record.setClarificationQuestion(text(result, "clarifying_question", ""));
        String qualityJson = text(result, "quality_json", "{}");
        record.setQualityJson(qualityJson);
        try {
            Map<String, Object> quality = objectMapper.readValue(
                    qualityJson, new TypeReference<Map<String, Object>>() {});
            Object score = quality.getOrDefault("model_score", quality.get("score"));
            if (score instanceof Number number) record.setQualityScore(number.intValue());
        } catch (Exception ignored) {
        }
        try {
            Map<String, Object> decision = new LinkedHashMap<>();
            for (String key : List.of("intent", "dialogue_state", "plan_action",
                    "requested_plan_action", "plan_revision_request", "revision_scope_json",
                    "needs_clarification", "clarifying_question", "temporary_state_json")) {
                decision.put(key, result.get(key));
            }
            record.setDecisionJson(objectMapper.writeValueAsString(decision));
        } catch (Exception e) {
            record.setDecisionJson("{}");
        }
        repository.save(record);
    }

    public List<AgentDecisionRecord> getRecent(Long userId, String conversationId, int limit) {
        return repository.findByUserIdAndConversationIdOrderByCreatedAtDesc(
                userId, conversationId, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
    }

    private String text(Map<String, Object> result, String key, String fallback) {
        Object value = result.get(key);
        String text = value == null ? fallback : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }
}
