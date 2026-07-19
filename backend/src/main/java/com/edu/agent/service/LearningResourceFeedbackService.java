package com.edu.agent.service;

import com.edu.agent.model.LearningResourceFeedback;
import com.edu.agent.repository.LearningResourceFeedbackRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class LearningResourceFeedbackService {
    private static final Set<String> EVENTS = Set.of("CLICK", "HELPFUL", "NOT_HELPFUL", "COMPLETED");
    private final LearningResourceFeedbackRepository repository;
    private final ObjectMapper objectMapper;

    public LearningResourceFeedbackService(LearningResourceFeedbackRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(Long userId, String conversationId, String url, String title, String event) {
        String safeUrl = url == null ? "" : url.trim();
        if (!safeUrl.startsWith("https://") || safeUrl.length() > 2000) {
            throw new IllegalArgumentException("资源链接无效");
        }
        String safeEvent = event == null ? "CLICK" : event.trim().toUpperCase(Locale.ROOT);
        if (!EVENTS.contains(safeEvent)) throw new IllegalArgumentException("资源反馈类型无效");
        String key = sha256(safeUrl);
        LearningResourceFeedback feedback = repository.findByUserIdAndResourceKey(userId, key)
                .orElseGet(LearningResourceFeedback::new);
        feedback.setUserId(userId);
        feedback.setConversationId(conversationId == null || conversationId.isBlank() ? null : conversationId.trim());
        feedback.setResourceKey(key);
        feedback.setResourceUrl(safeUrl);
        feedback.setResourceTitle(title == null ? "" : title.trim().substring(0, Math.min(title.trim().length(), 500)));
        switch (safeEvent) {
            case "CLICK" -> feedback.setClickCount(value(feedback.getClickCount()) + 1);
            case "HELPFUL" -> feedback.setHelpfulScore(1);
            case "NOT_HELPFUL" -> feedback.setHelpfulScore(-1);
            case "COMPLETED" -> feedback.setCompletedCount(value(feedback.getCompletedCount()) + 1);
        }
        repository.save(feedback);
    }

    @Transactional(readOnly = true)
    public String rankingJson(Long userId) {
        Map<String, Double> scores = new LinkedHashMap<>();
        repository.findTop100ByUserIdOrderByUpdatedAtDesc(userId).forEach(item -> {
            double score = value(item.getHelpfulScore()) * 45.0
                    + Math.min(20, value(item.getCompletedCount()) * 10.0)
                    + Math.min(8, Math.log1p(value(item.getClickCount())) * 2.5);
            scores.put(item.getResourceUrl(), score);
        });
        try { return objectMapper.writeValueAsString(scores); }
        catch (Exception ignored) { return "{}"; }
    }

    private int value(Integer value) { return value == null ? 0 : value; }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算资源标识", exception);
        }
    }
}
