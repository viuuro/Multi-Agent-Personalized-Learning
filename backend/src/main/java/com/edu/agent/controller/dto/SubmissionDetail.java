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

    private String conversationId;

    private String fileName;

    private Long fileSize;

    private Integer versionNumber;

    private Long previousSubmissionId;

    private Long comparisonSubmissionId;

    /** 陪伴用户查看成长反馈的智能体名称。 */

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
        dto.setConversationId(submission.getConversationId());
        dto.setFileName(submission.getFileName());
        dto.setFileSize(submission.getFileSize());
        dto.setVersionNumber(submission.getVersionNumber());
        dto.setPreviousSubmissionId(submission.getPreviousSubmissionId());
        dto.setComparisonSubmissionId(submission.getComparisonSubmissionId());
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
            evalDto.setWeaknessesJson(evaluation.getWeaknessesJson());
            evalDto.setRecommendedActionsJson(evaluation.getRecommendedActionsJson());
            evalDto.setDimensionsJson(evaluation.getDimensionsJson());
            evalDto.setStrengthsJson(evaluation.getStrengthsJson());
            evalDto.setMasteredPointsJson(evaluation.getMasteredPointsJson());
            evalDto.setProgressEvidenceJson(evaluation.getProgressEvidenceJson());
            evalDto.setBehaviorLinksJson(evaluation.getBehaviorLinksJson());
            evalDto.setGrowthOutcome(evaluation.getGrowthOutcome());
            evalDto.setPreviousScore(evaluation.getPreviousScore());
            evalDto.setScoreDelta(evaluation.getScoreDelta());
            evalDto.setNextChallenge(evaluation.getNextChallenge());
            evalDto.setBlessingText(evaluation.getBlessingText());
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

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }
    public Long getPreviousSubmissionId() { return previousSubmissionId; }
    public void setPreviousSubmissionId(Long previousSubmissionId) { this.previousSubmissionId = previousSubmissionId; }
    public Long getComparisonSubmissionId() { return comparisonSubmissionId; }
    public void setComparisonSubmissionId(Long comparisonSubmissionId) { this.comparisonSubmissionId = comparisonSubmissionId; }

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
        private String weaknessesJson;
        private String recommendedActionsJson;
        private String dimensionsJson;
        private String strengthsJson;
        private String masteredPointsJson;
        private String progressEvidenceJson;
        private String behaviorLinksJson;
        private String growthOutcome;
        private Integer previousScore;
        private Integer scoreDelta;
        private String nextChallenge;
        private String blessingText;
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

        public String getWeaknessesJson() { return weaknessesJson; }
        public void setWeaknessesJson(String weaknessesJson) { this.weaknessesJson = weaknessesJson; }
        public String getRecommendedActionsJson() { return recommendedActionsJson; }
        public void setRecommendedActionsJson(String recommendedActionsJson) { this.recommendedActionsJson = recommendedActionsJson; }
        public String getDimensionsJson() { return dimensionsJson; }
        public void setDimensionsJson(String dimensionsJson) { this.dimensionsJson = dimensionsJson; }
        public String getStrengthsJson() { return strengthsJson; }
        public void setStrengthsJson(String strengthsJson) { this.strengthsJson = strengthsJson; }
        public String getMasteredPointsJson() { return masteredPointsJson; }
        public void setMasteredPointsJson(String masteredPointsJson) { this.masteredPointsJson = masteredPointsJson; }
        public String getProgressEvidenceJson() { return progressEvidenceJson; }
        public void setProgressEvidenceJson(String progressEvidenceJson) { this.progressEvidenceJson = progressEvidenceJson; }
        public String getBehaviorLinksJson() { return behaviorLinksJson; }
        public void setBehaviorLinksJson(String behaviorLinksJson) { this.behaviorLinksJson = behaviorLinksJson; }
        public String getGrowthOutcome() { return growthOutcome; }
        public void setGrowthOutcome(String growthOutcome) { this.growthOutcome = growthOutcome; }
        public Integer getPreviousScore() { return previousScore; }
        public void setPreviousScore(Integer previousScore) { this.previousScore = previousScore; }
        public Integer getScoreDelta() { return scoreDelta; }
        public void setScoreDelta(Integer scoreDelta) { this.scoreDelta = scoreDelta; }
        public String getNextChallenge() { return nextChallenge; }
        public void setNextChallenge(String nextChallenge) { this.nextChallenge = nextChallenge; }
        public String getBlessingText() { return blessingText; }
        public void setBlessingText(String blessingText) { this.blessingText = blessingText; }

        public LocalDateTime getEvaluationTime() { return evaluationTime; }
        public void setEvaluationTime(LocalDateTime evaluationTime) { this.evaluationTime = evaluationTime; }
    }
}
