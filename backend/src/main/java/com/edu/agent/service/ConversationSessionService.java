package com.edu.agent.service;

import com.edu.agent.model.ConversationSession;
import com.edu.agent.repository.ConversationSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/** 管理对话标题等会话级元数据。 */
@Service
public class ConversationSessionService {

    private final ConversationSessionRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConversationSessionService(ConversationSessionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ConversationSession ensureSession(Long userId, String conversationId) {
        return repository.findByUserIdAndConversationId(userId, conversationId)
                .orElseGet(() -> {
                    ConversationSession session = new ConversationSession();
                    session.setUserId(userId);
                    session.setConversationId(conversationId);
                    session.setTitle("新对话");
                    return repository.save(session);
                });
    }

    @Transactional
    public String saveTitle(Long userId, String conversationId, String title) {
        ConversationSession session = ensureSession(userId, conversationId);
        String normalized = title == null ? "" : title.trim();
        if (normalized.isBlank()) normalized = "新对话";
        if (normalized.length() > 40) normalized = normalized.substring(0, 40);
        session.setTitle(normalized);
        return repository.save(session).getTitle();
    }

    @Transactional(readOnly = true)
    public Optional<ConversationSession> findSession(Long userId, String conversationId) {
        return repository.findByUserIdAndConversationId(userId, conversationId);
    }

    @Transactional
    public ConversationSession updateIntelligence(Long userId,
                                                  String conversationId,
                                                  String memorySummary,
                                                  String temporaryStateJson,
                                                  String intent,
                                                  String dialogueState,
                                                  String pendingQuestion,
                                                  String qualityJson) {
        ConversationSession session = ensureSession(userId, conversationId);
        session.setMemorySummary(limit(memorySummary, 12000));
        session.setTemporaryStateJson(limit(temporaryStateJson, 12000));
        session.setLastIntent(limit(intent, 40));
        session.setDialogueState(limit(dialogueState, 40));
        session.setPendingQuestion(limit(pendingQuestion, 500));
        session.setLastQualityJson(limit(qualityJson, 12000));
        return repository.save(session);
    }

    @Transactional
    public void recordSubmissionFeedback(Long userId,
                                         String conversationId,
                                         int score,
                                         String analysis,
                                         String suggestion) {
        if (conversationId == null || conversationId.isBlank()) return;
        ConversationSession session = ensureSession(userId, conversationId);
        Map<String, Object> state = new LinkedHashMap<>();
        try {
            if (session.getTemporaryStateJson() != null && !session.getTemporaryStateJson().isBlank()) {
                state.putAll(objectMapper.readValue(session.getTemporaryStateJson(),
                        new TypeReference<Map<String, Object>>() {}));
            }
        } catch (Exception ignored) {
        }
        state.put("lastSubmissionScore", score);
        state.put("lastSubmissionAnalysis", limit(analysis, 500));
        state.put("lastSubmissionSuggestion", limit(suggestion, 500));
        try {
            session.setTemporaryStateJson(objectMapper.writeValueAsString(state));
        } catch (Exception ignored) {
        }
        String summary = value(session.getMemorySummary());
        String feedback = "最近成果评分：" + score + "分；反馈：" + value(analysis);
        session.setMemorySummary(limit((summary + "\n" + feedback).trim(), 12000));
        repository.save(session);
    }

    @Transactional(readOnly = true)
    public Map<String, String> getTitleMap(Long userId) {
        return repository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        ConversationSession::getConversationId,
                        ConversationSession::getTitle,
                        (first, second) -> second));
    }

    private String limit(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
