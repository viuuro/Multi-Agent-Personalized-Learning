package com.edu.agent.controller.dto;

import com.edu.agent.model.AiEvaluation;
import com.edu.agent.model.TaskSubmission;
import java.time.LocalDateTime;

/**
 * 提交详情 DTO —— 包含提交内容及 AI 评价结果
 *
 * 当通过 GET /api/submissions/{id} 或 GET /api/tasks/{taskId}/submissions 查询时，
 * 返回此 DTO 格式的数据，包含用户提交的原始内容以及 AI 的评分和分析。
 *
 * ========== 返回数据示例 ==========
 *   {
 *     "code": 200,
 *     "message": "ok",
 *     "data": {
 *       "id": 1,
 *       "taskId": 1,
 *       "userId": 1,
 *       "content": "这是成果内容...",
 *       "submissionTime": "2024-01-01T10:00:00",
 *       "status": "EVALUATED",
 *       "errorMessage": null,
 *       "evaluation": {
 *         "id": 1,
 *         "score": 85,
 *         "analysis": "用户提交的成果基本完成了任务要求...",
 *         "suggestion": "1. 建议补充更多实践案例...",
 *         "evaluationTime": "2024-01-01T10:00:30"
 *       }
 *     }
 *   }
 *
 * ========== 特殊说明 ==========
 *   - 当 status=PENDING 时，evaluation 字段为 null（AI 尚未完成评价）
 *   - 当 status=ERROR 时，evaluation 字段为 null，errorMessage 包含失败原因
 *   - 当 status=EVALUATED 时，evaluation 包含完整的 AI 评价信息
 */
public class SubmissionDetail {

    /** 提交记录 ID */
    private Long id;

    /** 关联的任务 ID */
    private Long taskId;

    /** 提交者用户 ID */
    private Long userId;

    /** 提交的成果内容 */
    private String content;

    /** 提交时间 */
    private LocalDateTime submissionTime;

    /** 状态：PENDING（待评价）/ EVALUATED（已评价）/ ERROR（评价失败） */
    private String status;

    /** AI 评价错误信息（仅 status=ERROR 时有值） */
    private String errorMessage;

    /** AI 评价信息（status=EVALUATED 时有值，其他情况为 null） */
    private AiEvaluationDto evaluation;

    /**
     * 工厂方法 —— 从 TaskSubmission 和 AiEvaluation 构建 SubmissionDetail
     *
     * @param submission 提交记录实体（必填）
     * @param evaluation AI 评价实体（可能为 null）
     * @return 组装好的 DTO
     */
    public static SubmissionDetail from(TaskSubmission submission, AiEvaluation evaluation) {
        SubmissionDetail dto = new SubmissionDetail();
        dto.setId(submission.getId());
        dto.setTaskId(submission.getTaskId());
        dto.setUserId(submission.getUserId());
        dto.setContent(submission.getContent());
        dto.setSubmissionTime(submission.getSubmissionTime());
        dto.setStatus(submission.getStatus());
        dto.setErrorMessage(submission.getErrorMessage());
        if (evaluation != null) {
            AiEvaluationDto evalDto = new AiEvaluationDto();
            evalDto.setId(evaluation.getId());
            evalDto.setScore(evaluation.getScore());
            evalDto.setAnalysis(evaluation.getAnalysis());
            evalDto.setSuggestion(evaluation.getSuggestion());
            evalDto.setEvaluationTime(evaluation.getEvaluationTime());
            dto.setEvaluation(evalDto);
        }
        return dto;
    }

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getSubmissionTime() { return submissionTime; }
    public void setSubmissionTime(LocalDateTime submissionTime) { this.submissionTime = submissionTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public AiEvaluationDto getEvaluation() { return evaluation; }
    public void setEvaluation(AiEvaluationDto evaluation) { this.evaluation = evaluation; }

    /**
     * AI 评价信息内部 DTO —— 仅包含前端需要的评价字段，不暴露实体内部细节
     *
     * ========== 字段说明 ==========
     *   score           — AI 评分（百分制 0-100）
     *   analysis        — AI 详细分析（从完成度、质量等维度的文字评价）
     *   suggestion      — AI 改进建议（具体的可操作建议）
     *   evaluationTime  — AI 评价完成时间
     */
    public static class AiEvaluationDto {
        /** AI 评价记录 ID */
        private Long id;
        /** AI 评分（百分制 0-100） */
        private Integer score;
        /** AI 详细分析 */
        private String analysis;
        /** AI 改进建议 */
        private String suggestion;
        /** AI 评价完成时间 */
        private LocalDateTime evaluationTime;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }

        public String getAnalysis() { return analysis; }
        public void setAnalysis(String analysis) { this.analysis = analysis; }

        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

        public LocalDateTime getEvaluationTime() { return evaluationTime; }
        public void setEvaluationTime(LocalDateTime evaluationTime) { this.evaluationTime = evaluationTime; }
    }
}