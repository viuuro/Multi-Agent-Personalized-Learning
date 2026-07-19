package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.security.CurrentUser;
import com.edu.agent.service.LearningResourceFeedbackService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/resources/feedback")
public class LearningResourceFeedbackController {
    private final LearningResourceFeedbackService service;

    public LearningResourceFeedbackController(LearningResourceFeedbackService service) { this.service = service; }

    @PostMapping
    public ApiResponse<Void> record(@RequestBody Map<String, Object> body, Authentication authentication) {
        service.record(CurrentUser.id(authentication),
                String.valueOf(body.getOrDefault("conversationId", "")),
                String.valueOf(body.getOrDefault("url", "")),
                String.valueOf(body.getOrDefault("title", "")),
                String.valueOf(body.getOrDefault("event", "CLICK")));
        return ApiResponse.success("资源反馈已记录", null);
    }
}
