package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.ConversationSession;
import com.edu.agent.service.AgentDecisionService;
import com.edu.agent.service.ConversationSessionService;
import com.edu.agent.service.LearningPlanVersionService;
import com.edu.agent.service.ProfileEvidenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import com.edu.agent.security.CurrentUser;

import java.util.LinkedHashMap;
import java.util.Map;

/** 提供智能状态、画像证据和决策记录，便于验证与评测。 */
@RestController
@RequestMapping("/api/conversations/{conversationId}/intelligence")
public class IntelligenceController {

    private final ConversationSessionService sessionService;
    private final ProfileEvidenceService evidenceService;
    private final AgentDecisionService decisionService;
    private final LearningPlanVersionService planVersionService;

    public IntelligenceController(ConversationSessionService sessionService,
                                  ProfileEvidenceService evidenceService,
                                  AgentDecisionService decisionService,
                                  LearningPlanVersionService planVersionService) {
        this.sessionService = sessionService;
        this.evidenceService = evidenceService;
        this.decisionService = decisionService;
        this.planVersionService = planVersionService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getIntelligenceState(
            @PathVariable String conversationId,
            @RequestParam(required = false) Long userId,
            Authentication authentication) {
        userId = CurrentUser.id(authentication);
        Map<String, Object> data = new LinkedHashMap<>();
        ConversationSession session = sessionService.findSession(userId, conversationId).orElse(null);
        data.put("memorySummary", session == null ? "" : session.getMemorySummary());
        data.put("temporaryStateJson", session == null ? "{}" : session.getTemporaryStateJson());
        data.put("lastIntent", session == null ? "" : session.getLastIntent());
        data.put("dialogueState", session == null ? "" : session.getDialogueState());
        data.put("pendingQuestion", session == null ? "" : session.getPendingQuestion());
        data.put("lastQualityJson", session == null ? "{}" : session.getLastQualityJson());
        data.put("profileEvidence", evidenceService.getEvidence(userId, conversationId));
        data.put("recentDecisions", decisionService.getRecent(userId, conversationId, 30));
        data.put("planVersions", planVersionService.getHistory(userId, conversationId));
        return ApiResponse.success("ok", data);
    }
}
