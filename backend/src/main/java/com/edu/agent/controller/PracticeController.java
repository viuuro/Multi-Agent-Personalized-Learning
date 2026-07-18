package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.security.CurrentUser;
import com.edu.agent.service.PracticeQuestionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/practice/questions")
public class PracticeController {
    private final PracticeQuestionService service;

    public PracticeController(PracticeQuestionService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<PracticeQuestionService.QuestionView>> list(
            @RequestParam String conversationId, Authentication authentication) {
        return ApiResponse.success("ok", service.list(CurrentUser.id(authentication), conversationId));
    }

    @PostMapping("/generate")
    public ApiResponse<List<PracticeQuestionService.QuestionView>> generate(
            @RequestBody Map<String, Object> body, Authentication authentication) {
        String conversationId = String.valueOf(body.getOrDefault("conversationId", "")).trim();
        int weekNumber = number(body.get("weekNumber"), 1);
        int taskIndex = number(body.get("taskIndex"), 0);
        int count = number(body.get("count"), 3);
        String type = String.valueOf(body.getOrDefault("questionType", "SINGLE_CHOICE"));
        String difficulty = String.valueOf(body.getOrDefault("difficulty", "MEDIUM"));
        return ApiResponse.success("题目已生成", service.generate(
                CurrentUser.id(authentication), conversationId, weekNumber, taskIndex, type, difficulty, count));
    }

    @PutMapping("/{questionId}/answer")
    public ApiResponse<PracticeQuestionService.QuestionView> saveAnswer(
            @PathVariable Long questionId, @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String answer = String.valueOf(body.getOrDefault("answer", ""));
        return ApiResponse.success("答案已保存", service.saveDraft(CurrentUser.id(authentication), questionId, answer));
    }

    @PostMapping("/{questionId}/submit")
    public ApiResponse<PracticeQuestionService.QuestionView> submit(
            @PathVariable Long questionId, @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String answer = String.valueOf(body.getOrDefault("answer", ""));
        return ApiResponse.success("作答已提交", service.submit(CurrentUser.id(authentication), questionId, answer));
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }
}
