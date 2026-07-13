package com.edu.agent.service;

import com.edu.agent.model.Conversation;
import com.edu.agent.model.TaskSubmission;
import com.edu.agent.repository.ConversationRepository;
import com.edu.agent.repository.TaskSubmissionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 根据真实学习行为计算每日学习活跃度。 */
@Service
public class LearningActivityService {

    private final ConversationRepository conversationRepository;
    private final TaskSubmissionRepository submissionRepository;

    public LearningActivityService(ConversationRepository conversationRepository,
                                   TaskSubmissionRepository submissionRepository) {
        this.conversationRepository = conversationRepository;
        this.submissionRepository = submissionRepository;
    }

    public List<DailyActivity> getDailyActivity(Long userId, int requestedDays) {
        int days = Math.max(1, Math.min(requestedDays, 365));
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();

        List<Conversation> userMessages = conversationRepository
                .findByUserIdAndRoleAndTimestampBetween(userId, "user", start, endExclusive);
        List<TaskSubmission> submissions = submissionRepository
                .findByUserIdAndSubmissionTimeBetween(userId, start, endExclusive);

        Map<LocalDate, Long> messageCounts = userMessages.stream()
                .filter(item -> item.getTimestamp() != null)
                .collect(Collectors.groupingBy(
                        item -> item.getTimestamp().toLocalDate(), Collectors.counting()));
        Map<LocalDate, List<TaskSubmission>> submissionsByDay = submissions.stream()
                .filter(item -> item.getSubmissionTime() != null)
                .collect(Collectors.groupingBy(item -> item.getSubmissionTime().toLocalDate()));

        List<DailyActivity> result = new ArrayList<>(days);
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = startDate.plusDays(offset);
            int conversationCount = messageCounts.getOrDefault(date, 0L).intValue();
            List<TaskSubmission> dailySubmissions = submissionsByDay.getOrDefault(date, List.of());
            int submissionCount = dailySubmissions.size();
            int evaluatedCount = (int) dailySubmissions.stream()
                    .filter(item -> TaskSubmission.STATUS_EVALUATED.equals(item.getStatus()))
                    .count();

            // 每日对话最多计 60 分，成果提交最多计 60 分，已评价成果给少量质量加成。
            int score = Math.min(100,
                    Math.min(conversationCount, 10) * 6
                            + Math.min(submissionCount, 3) * 20
                            + Math.min(evaluatedCount, 2) * 5);
            int level = toLevel(score);
            result.add(new DailyActivity(date, conversationCount, submissionCount,
                    evaluatedCount, score, level));
        }
        return result;
    }

    private int toLevel(int score) {
        if (score <= 0) return 0;
        if (score < 20) return 1;
        if (score < 40) return 2;
        if (score < 70) return 3;
        return 4;
    }

    public record DailyActivity(
            LocalDate date,
            int conversationCount,
            int submissionCount,
            int evaluatedCount,
            int score,
            int level
    ) {}
}
