package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.service.LearningActivityService;
import com.edu.agent.service.LearningActivityService.DailyActivity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import com.edu.agent.security.CurrentUser;

import java.util.List;

/** 学习活跃度接口。 */
@RestController
@RequestMapping("/api/activity")
public class LearningActivityController {

    private final LearningActivityService activityService;

    public LearningActivityController(LearningActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ApiResponse<List<DailyActivity>> getActivity(
            @RequestParam(required = false) Long userId,
            Authentication authentication,
            @RequestParam(defaultValue = "112") int days) {
        userId = CurrentUser.id(authentication);
        return ApiResponse.success("ok", activityService.getDailyActivity(userId, days));
    }
}
