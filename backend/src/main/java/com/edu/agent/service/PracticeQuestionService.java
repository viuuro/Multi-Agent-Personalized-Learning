package com.edu.agent.service;

import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.LearningPlanEntity;
import com.edu.agent.model.PracticeQuestion;
import com.edu.agent.model.UserProfile;
import com.edu.agent.repository.LearningPlanRepository;
import com.edu.agent.repository.PracticeQuestionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PracticeQuestionService {
    private static final Set<String> TYPES = Set.of(
            "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER");
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");

    private final PracticeQuestionRepository repository;
    private final LearningPlanRepository planRepository;
    private final ProfileService profileService;
    private final QuestionAiService questionAiService;
    private final ObjectMapper objectMapper;

    public PracticeQuestionService(PracticeQuestionRepository repository,
                                   LearningPlanRepository planRepository,
                                   ProfileService profileService,
                                   QuestionAiService questionAiService,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.planRepository = planRepository;
        this.profileService = profileService;
        this.questionAiService = questionAiService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<QuestionView> generate(Long userId, String conversationId, int weekNumber,
                                       int taskIndex, String type, String difficulty, int count) {
        if (conversationId == null || conversationId.isBlank()) throw new IllegalArgumentException("缺少对话 ID");
        type = normalizeEnum(type, TYPES, "SINGLE_CHOICE");
        difficulty = normalizeEnum(difficulty, DIFFICULTIES, "MEDIUM");
        count = Math.max(1, Math.min(count, 10));

        LearningPlan plan = currentPlan(userId, conversationId);
        LearningPlan.PlanWeek week = plan.getWeeks().stream()
                .filter(item -> item.getWeekNumber() == weekNumber).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("所选周计划不存在"));
        if (week.getTasks() == null || taskIndex < 0 || taskIndex >= week.getTasks().size()) {
            throw new IllegalArgumentException("所选学习任务不存在");
        }
        String taskTitle = week.getTasks().get(taskIndex);
        UserProfile profile = profileService.getCurrentProfile(userId, conversationId);
        List<QuestionAiService.GeneratedQuestion> generated = questionAiService.generate(
                profile.getProfileJson(), week.getTopic(), taskTitle, type, difficulty, count);
        if (generated.isEmpty()) throw new IllegalArgumentException("题目生成失败，请稍后重试");

        String batchId = UUID.randomUUID().toString();
        List<PracticeQuestion> saved = new ArrayList<>();
        for (QuestionAiService.GeneratedQuestion item : generated) {
            PracticeQuestion question = new PracticeQuestion();
            question.setUserId(userId);
            question.setConversationId(conversationId);
            question.setBatchId(batchId);
            question.setWeekNumber(weekNumber);
            question.setTaskIndex(taskIndex);
            question.setWeekTopic(week.getTopic());
            question.setTaskTitle(taskTitle);
            question.setQuestionType(type);
            question.setDifficulty(difficulty);
            question.setQuestionText(item.question());
            question.setOptionsJson(writeJson(item.options()));
            question.setCorrectAnswer(item.correctAnswer());
            question.setExplanation(item.explanation());
            saved.add(repository.save(question));
        }
        return saved.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public List<QuestionView> list(Long userId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return List.of();
        return repository.findByUserIdAndConversationIdOrderByCreatedAtDescIdAsc(userId, conversationId)
                .stream().map(this::toView).toList();
    }

    @Transactional
    public QuestionView saveDraft(Long userId, Long questionId, String answer) {
        PracticeQuestion question = ownedQuestion(userId, questionId);
        if (PracticeQuestion.STATUS_SUBMITTED.equals(question.getStatus())) return toView(question);
        String safeAnswer = answer == null ? "" : answer.trim();
        question.setUserAnswer(safeAnswer);
        question.setStatus(safeAnswer.isBlank()
                ? PracticeQuestion.STATUS_UNANSWERED : PracticeQuestion.STATUS_DRAFT);
        question.setCorrect(null);
        question.setScore(null);
        return toView(repository.save(question));
    }

    @Transactional
    public QuestionView submit(Long userId, Long questionId, String answer) {
        PracticeQuestion question = ownedQuestion(userId, questionId);
        String safeAnswer = answer == null ? "" : answer.trim();
        if (safeAnswer.isBlank()) throw new IllegalArgumentException("请先填写答案");
        int score = grade(question, safeAnswer);
        question.setUserAnswer(safeAnswer);
        question.setStatus(PracticeQuestion.STATUS_SUBMITTED);
        question.setScore(score);
        question.setCorrect(score >= 80);
        question.setSubmittedAt(LocalDateTime.now());
        return toView(repository.save(question));
    }

    private LearningPlan currentPlan(Long userId, String conversationId) {
        LearningPlanEntity entity = planRepository
                .findFirstByUserIdAndConversationIdOrderByUpdatedAtDesc(userId, conversationId)
                .orElseThrow(() -> new IllegalArgumentException("请先生成学习计划，再按任务生成题目"));
        try {
            LearningPlan plan = objectMapper.readValue(entity.getPlanJson(), LearningPlan.class);
            if (plan.getWeeks() == null || plan.getWeeks().isEmpty()) throw new IllegalArgumentException("学习计划暂无任务");
            return plan;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("学习计划数据无法解析");
        }
    }

    private PracticeQuestion ownedQuestion(Long userId, Long questionId) {
        return repository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("题目不存在或无权访问"));
    }

    private int grade(PracticeQuestion question, String answer) {
        if (!"SHORT_ANSWER".equals(question.getQuestionType())) {
            return normalizedChoice(answer).equals(normalizedChoice(question.getCorrectAnswer())) ? 100 : 0;
        }
        List<String> keywords = Arrays.stream(question.getCorrectAnswer().split("[；;,，、\\s]+"))
                .map(String::trim).filter(value -> value.length() >= 2).toList();
        if (keywords.isEmpty()) return answer.length() >= 30 ? 80 : 60;
        long matched = keywords.stream().filter(answer::contains).count();
        int coverage = (int) Math.round(matched * 100.0 / keywords.size());
        int detailBonus = answer.length() >= 80 ? 15 : answer.length() >= 35 ? 8 : 0;
        return Math.min(100, Math.max(30, coverage + detailBonus));
    }

    private String normalizedChoice(String answer) {
        return Arrays.stream(answer.toUpperCase(Locale.ROOT).split("[,，、\\s]+"))
                .map(String::trim).filter(value -> !value.isBlank()).sorted().reduce((a, b) -> a + "," + b).orElse("");
    }

    private String normalizeEnum(String value, Set<String> allowed, String fallback) {
        String normalized = value == null ? fallback : value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { return "[]"; }
    }

    private List<String> readOptions(String value) {
        try { return objectMapper.readValue(value == null ? "[]" : value, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private QuestionView toView(PracticeQuestion question) {
        boolean submitted = PracticeQuestion.STATUS_SUBMITTED.equals(question.getStatus());
        return new QuestionView(
                question.getId(), question.getBatchId(), question.getConversationId(),
                question.getWeekNumber(), question.getTaskIndex(), question.getWeekTopic(), question.getTaskTitle(),
                question.getQuestionType(), question.getDifficulty(), question.getQuestionText(),
                readOptions(question.getOptionsJson()), question.getUserAnswer(), question.getStatus(),
                submitted ? question.getCorrectAnswer() : null,
                submitted ? question.getExplanation() : null,
                submitted ? question.getCorrect() : null,
                submitted ? question.getScore() : null,
                question.getCreatedAt(), question.getUpdatedAt(), question.getSubmittedAt());
    }

    public record QuestionView(
            Long id, String batchId, String conversationId, Integer weekNumber, Integer taskIndex,
            String weekTopic, String taskTitle, String questionType, String difficulty,
            String questionText, List<String> options, String userAnswer, String status,
            String correctAnswer, String explanation, Boolean correct, Integer score,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime submittedAt) {}
}
