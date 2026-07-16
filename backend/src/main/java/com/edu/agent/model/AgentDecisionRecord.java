package com.edu.agent.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 每轮智能体的路由、澄清、计划动作与质量检查记录。 */
@Entity
@Table(name = "agent_decision_record", indexes = {
        @Index(name = "idx_agent_decision_owner", columnList = "user_id,conversation_id,created_at")
})
public class AgentDecisionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;
    @Column(name = "message_id")
    private Long messageId;
    @Column(name = "intent", length = 40)
    private String intent;
    @Column(name = "dialogue_state", length = 40)
    private String dialogueState;
    @Column(name = "plan_action", length = 40)
    private String planAction;
    @Column(name = "clarification_required", nullable = false)
    private Boolean clarificationRequired = false;
    @Column(name = "clarification_question", length = 500)
    private String clarificationQuestion;
    @Column(name = "quality_score")
    private Integer qualityScore;
    @Column(name = "quality_json", columnDefinition = "TEXT")
    private String qualityJson;
    @Column(name = "decision_json", columnDefinition = "MEDIUMTEXT")
    private String decisionJson;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public String getDialogueState() { return dialogueState; }
    public void setDialogueState(String dialogueState) { this.dialogueState = dialogueState; }
    public String getPlanAction() { return planAction; }
    public void setPlanAction(String planAction) { this.planAction = planAction; }
    public Boolean getClarificationRequired() { return clarificationRequired; }
    public void setClarificationRequired(Boolean clarificationRequired) { this.clarificationRequired = clarificationRequired; }
    public String getClarificationQuestion() { return clarificationQuestion; }
    public void setClarificationQuestion(String clarificationQuestion) { this.clarificationQuestion = clarificationQuestion; }
    public Integer getQualityScore() { return qualityScore; }
    public void setQualityScore(Integer qualityScore) { this.qualityScore = qualityScore; }
    public String getQualityJson() { return qualityJson; }
    public void setQualityJson(String qualityJson) { this.qualityJson = qualityJson; }
    public String getDecisionJson() { return decisionJson; }
    public void setDecisionJson(String decisionJson) { this.decisionJson = decisionJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
