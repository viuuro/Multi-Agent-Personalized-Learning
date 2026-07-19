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
    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectMapper objectMapper;

    public PracticeQuestionService(PracticeQuestionRepository repository,
                                   LearningPlanRepository planRepository,
                                   ProfileService profileService,
                                   QuestionAiService questionAiService,
                                   KnowledgeBaseService knowledgeBaseService,
                                   ObjectMapper objectMapper) {
        this.repository = repository;
        this.planRepository = planRepository;
        this.profileService = profileService;
        this.questionAiService = questionAiService;
        this.knowledgeBaseService = knowledgeBaseService;
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
        KnowledgeContext knowledge = retrieveKnowledge(
                userId, conversationId, week.getTopic(), taskTitle);
        List<QuestionAiService.GeneratedQuestion> history = repository
                .findTop40ByUserIdAndConversationIdAndWeekNumberAndTaskIndexOrderByCreatedAtDesc(
                        userId, conversationId, weekNumber, taskIndex)
                .stream().map(item -> new QuestionAiService.GeneratedQuestion(
                        item.getQuestionText(), readOptions(item.getOptionsJson()), item.getCorrectAnswer(),
                        item.getExplanation(), item.getKnowledgePoint(), item.getLearningObjective(),
                        item.getCognitiveLevel(), readSourceChunkIds(item.getSourceChunkIdsJson()),
                        item.getQualityScore())).toList();
        long totalHistoricalCount = repository.countByUserIdAndConversationIdAndWeekNumberAndTaskIndex(
                userId, conversationId, weekNumber, taskIndex);
        List<QuestionAiService.GeneratedQuestion> generated = questionAiService.generate(
                profile.getProfileJson(), week.getTopic(), taskTitle, type, difficulty, count,
                knowledge.content(), knowledge.chunkIds(), history,
                (int) Math.min(Integer.MAX_VALUE, totalHistoricalCount));
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
            question.setKnowledgePoint(item.knowledgePoint());
            question.setLearningObjective(item.learningObjective());
            question.setCognitiveLevel(item.cognitiveLevel());
            question.setSourceChunkIdsJson(writeJson(item.sourceChunkIds()));
            question.setQualityScore(item.qualityScore());
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
        PracticeQuestion saved = repository.save(question);
        updateCourseProfileFromPractice(userId, saved);
        return toView(saved);
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

    private KnowledgeContext retrieveKnowledge(Long userId, String conversationId,
                                               String weekTopic, String taskTitle) {
        String query = String.join(" ",
                weekTopic == null ? "" : weekTopic,
                taskTitle == null ? "" : taskTitle).trim();
        List<KnowledgeBaseService.SearchResult> results =
                knowledgeBaseService.search(userId, conversationId, query, 12);
        if (results.isEmpty()) return new KnowledgeContext("", List.of());

        List<KnowledgeBaseService.SearchResult> facts = results.stream()
                .filter(result -> !isAssessmentGuidance(result.heading()))
                .limit(6).toList();
        List<KnowledgeBaseService.SearchResult> guidance = results.stream()
                .filter(result -> isAssessmentGuidance(result.heading()))
                .limit(2).toList();
        List<KnowledgeBaseService.SearchResult> selected = new ArrayList<>(facts);
        selected.addAll(guidance);
        if (selected.isEmpty()) selected.addAll(results.stream().limit(6).toList());

        StringBuilder context = new StringBuilder();
        List<Long> chunkIds = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            KnowledgeBaseService.SearchResult result = selected.get(index);
            chunkIds.add(result.chunkId());
            context.append("[资料").append(index + 1)
                    .append("; chunkId=").append(result.chunkId())
                    .append("; 用途=")
                    .append(isAssessmentGuidance(result.heading()) ? "命题指导" : "知识依据")
                    .append("; 文档=").append(result.documentTitle());
            if (result.heading() != null && !result.heading().isBlank()) {
                context.append("; 章节=").append(result.heading());
            }
            context.append("]\n").append(result.content().trim()).append("\n\n");
        }
        return new KnowledgeContext(context.toString().trim(), chunkIds.stream().distinct().toList());
    }

    private boolean isAssessmentGuidance(String heading) {
        return heading != null && (heading.contains("第二部分：出题篇")
                || heading.contains("题型与分值") || heading.contains("命题"));
    }

    private void updateCourseProfileFromPractice(Long userId, PracticeQuestion latest) {
        String family = courseFamily(latest);
        if (family == null) return;
        List<PracticeQuestion> submitted = repository
                .findByUserIdAndConversationIdOrderByCreatedAtDescIdAsc(
                        userId, latest.getConversationId())
                .stream()
                .filter(item -> PracticeQuestion.STATUS_SUBMITTED.equals(item.getStatus()))
                .filter(item -> family.equals(courseFamily(item)))
                .toList();
        if (submitted.isEmpty()) return;

        double weightedTotal = 0;
        double totalWeight = 0;
        LinkedHashMap<String, Integer> latestScores = new LinkedHashMap<>();
        for (PracticeQuestion item : submitted) {
            double weight = switch (item.getDifficulty() == null ? "" : item.getDifficulty()) {
                case "HARD" -> 1.45;
                case "MEDIUM" -> 1.20;
                default -> 1.0;
            };
            weightedTotal += (item.getScore() == null ? 0 : item.getScore()) * weight;
            totalWeight += weight;
            String point = item.getKnowledgePoint() == null || item.getKnowledgePoint().isBlank()
                    ? item.getTaskTitle() : item.getKnowledgePoint();
            latestScores.putIfAbsent(point.trim(), item.getScore() == null ? 0 : item.getScore());
        }
        int courseScore = (int) Math.round(weightedTotal / Math.max(1.0, totalWeight));
        String courseLabel = "DATA_STRUCTURE".equals(family) ? "数据结构" : "计组";
        List<String> weaknesses = latestScores.entrySet().stream()
                .filter(entry -> entry.getValue() < 80)
                .map(entry -> courseLabel + "·" + entry.getKey())
                .limit(4).toList();
        profileService.applyPracticeAssessment(userId, latest.getConversationId(),
                courseLabel, courseScore, submitted.size(), weaknesses);
    }

    private String courseFamily(PracticeQuestion question) {
        String text = String.join(" ",
                Objects.toString(question.getWeekTopic(), ""),
                Objects.toString(question.getTaskTitle(), ""),
                Objects.toString(question.getKnowledgePoint(), "")).toLowerCase(Locale.ROOT);
        if (containsAny(text, "计算机组成", "计组", "cache", "存储系统", "指令系统", "寻址",
                "数据通路", "控制器", "流水线", "总线", "中断", "dma", "补码", "浮点数")) {
            return "COMPUTER_ORGANIZATION";
        }
        if (containsAny(text, "数据结构", "线性表", "链表", "栈", "队列", "二叉树",
                "散列表", "哈希", "排序", "查找", "最短路径", "拓扑", "并查集")) {
            return "DATA_STRUCTURE";
        }
        return null;
    }

    private boolean containsAny(String text, String... terms) {
        return Arrays.stream(terms).anyMatch(text::contains);
    }

    private List<String> readOptions(String value) {
        try { return objectMapper.readValue(value == null ? "[]" : value, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private List<Long> readSourceChunkIds(String value) {
        try { return objectMapper.readValue(value == null ? "[]" : value, new TypeReference<List<Long>>() {}); }
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
                question.getKnowledgePoint(), question.getLearningObjective(), question.getCognitiveLevel(),
                readSourceChunkIds(question.getSourceChunkIdsJson()), question.getQualityScore(),
                question.getCreatedAt(), question.getUpdatedAt(), question.getSubmittedAt());
    }

    private record KnowledgeContext(String content, List<Long> chunkIds) {}

    public record QuestionView(
            Long id, String batchId, String conversationId, Integer weekNumber, Integer taskIndex,
            String weekTopic, String taskTitle, String questionType, String difficulty,
            String questionText, List<String> options, String userAnswer, String status,
            String correctAnswer, String explanation, Boolean correct, Integer score,
            String knowledgePoint, String learningObjective, String cognitiveLevel,
            List<Long> sourceChunkIds, Integer qualityScore,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime submittedAt) {}
}
