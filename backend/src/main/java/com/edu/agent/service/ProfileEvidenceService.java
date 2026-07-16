package com.edu.agent.service;

import com.edu.agent.model.ProfileEvidence;
import com.edu.agent.repository.ProfileEvidenceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 保存可追溯画像证据，并处理重复、替代与冲突。 */
@Service
public class ProfileEvidenceService {

    private static final Set<String> DIMENSIONS = Set.of(
            "knowledgeBase", "cognitiveStyle", "weaknessPoints",
            "learningPace", "interestAreas", "shortTermGoal");

    private final ProfileEvidenceRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProfileEvidenceService(ProfileEvidenceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Set<String> persist(Long userId, String conversationId, Long sourceMessageId, String evidenceJson) {
        Set<String> acceptedDimensions = new java.util.HashSet<>();
        if (userId == null || conversationId == null || conversationId.isBlank()
                || evidenceJson == null || evidenceJson.isBlank()) return acceptedDimensions;
        try {
            List<Map<String, Object>> items = objectMapper.readValue(
                    evidenceJson, new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> item : items.stream().limit(12).toList()) {
                String accepted = persistOne(userId, conversationId, sourceMessageId, item);
                if (accepted != null) acceptedDimensions.add(accepted);
            }
        } catch (Exception ignored) {
            // 非法模型输出不应中断主对话。
        }
        return acceptedDimensions;
    }

    private String persistOne(Long userId, String conversationId, Long sourceMessageId,
                              Map<String, Object> item) throws Exception {
        String dimension = String.valueOf(item.getOrDefault("dimension", ""));
        String evidenceText = String.valueOf(item.getOrDefault("evidence", "")).trim();
        double confidence = number(item.get("confidence"), 0.5);
        if (!DIMENSIONS.contains(dimension) || evidenceText.isBlank() || confidence < 0.60) return null;

        String valueJson = objectMapper.writeValueAsString(item.get("value"));
        String scope = "SHORT_TERM".equalsIgnoreCase(String.valueOf(item.get("scope")))
                ? "SHORT_TERM" : "LONG_TERM";
        String action = String.valueOf(item.getOrDefault("action", "retain"));
        if ("merge".equalsIgnoreCase(action)
                && !Set.of("interestAreas", "weaknessPoints").contains(dimension)) {
            action = "retain";
        }
        List<ProfileEvidence> active = repository
                .findByUserIdAndConversationIdAndDimensionAndStatusOrderByCreatedAtDesc(
                        userId, conversationId, dimension, "ACTIVE");

        if (active.stream().anyMatch(existing -> existing.getValueJson().equals(valueJson)
                && existing.getEvidenceText().equals(evidenceText))) {
            return "LONG_TERM".equals(scope) ? dimension : null;
        }

        String status = "ACTIVE";
        if (!active.isEmpty() && !"merge".equalsIgnoreCase(action)) {
            double strongest = active.stream().mapToDouble(ProfileEvidence::getConfidence).max().orElse(0.0);
            boolean canReplace = "replace".equalsIgnoreCase(action)
                    ? confidence + 0.05 >= strongest
                    : confidence >= strongest + 0.15;
            if (canReplace) {
                active.forEach(existing -> existing.setStatus("SUPERSEDED"));
                repository.saveAll(active);
            } else if (active.stream().noneMatch(existing -> existing.getValueJson().equals(valueJson))) {
                status = "CONFLICT";
            }
        }

        ProfileEvidence evidence = new ProfileEvidence();
        evidence.setUserId(userId);
        evidence.setConversationId(conversationId);
        evidence.setDimension(dimension);
        evidence.setValueJson(valueJson);
        evidence.setEvidenceText(evidenceText.length() > 1000 ? evidenceText.substring(0, 1000) : evidenceText);
        evidence.setSourceMessageId(sourceMessageId);
        evidence.setConfidence(Math.max(0.0, Math.min(1.0, confidence)));
        evidence.setScope(scope);
        evidence.setStatus(status);
        repository.save(evidence);
        return "ACTIVE".equals(status) && "LONG_TERM".equals(scope) ? dimension : null;
    }

    @Transactional(readOnly = true)
    public String buildEvidenceJson(Long userId, String conversationId) {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            repository.findByUserIdAndConversationIdOrderByCreatedAtDesc(userId, conversationId)
                    .stream().limit(50).forEach(item -> {
                        Map<String, Object> value = new LinkedHashMap<>();
                        value.put("dimension", item.getDimension());
                        try {
                            value.put("value", objectMapper.readValue(item.getValueJson(), Object.class));
                        } catch (Exception e) {
                            value.put("value", item.getValueJson());
                        }
                        value.put("evidence", item.getEvidenceText());
                        value.put("confidence", item.getConfidence());
                        value.put("scope", item.getScope());
                        value.put("status", item.getStatus());
                        result.add(value);
                    });
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "[]";
        }
    }

    @Transactional(readOnly = true)
    public List<ProfileEvidence> getEvidence(Long userId, String conversationId) {
        return repository.findByUserIdAndConversationIdOrderByCreatedAtDesc(userId, conversationId);
    }

    private double number(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return fallback;
        }
    }
}
