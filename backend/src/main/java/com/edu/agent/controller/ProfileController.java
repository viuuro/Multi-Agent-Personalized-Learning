package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.UserProfile;
import com.edu.agent.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import com.edu.agent.security.CurrentUser;

/**
 * 画像控制器 —— 提供用户画像查询接口
 *
 * GET /api/profile 返回当前用户的 6 维画像数据，
 * 前端在页面加载时调用此接口初始化雷达图。
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @RestController: 声明为 REST 控制器，返回值自动序列化为 JSON
 *   - @RequestMapping("/api"): 统一 URL 前缀
 *   - @GetMapping("/profile"): 映射 GET 请求到指定路径
 *   - 构造器注入: ProfileService 由 Spring 自动装配
 */
@RestController  // 【Spring Boot】声明为 REST 控制器
@RequestMapping("/api")  // 【Spring Boot】为所有接口添加 /api 前缀
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileService profileService;  // 【Spring Boot】构造器注入的用户画像服务

    /** 【Spring Boot】构造器注入 —— Spring 自动解析并注入 ProfileService Bean */
    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * GET /api/profile —— 获取当前用户 6 维画像
     *
     * 返回格式：
     *   {
     *     code: 200,
     *     data: {
     *       knowledgeBase: 7,
     *       cognitiveStyle: "kinesthetic",
     *       weaknessPoints: ["并发编程", "设计模式"],
     *       learningPace: 5,
     *       interestAreas: ["编程", "软件工程"],
     *       shortTermGoal: "成为全栈开发工程师"
     *     }
     *   }
     */
    @GetMapping("/profile")  // 【Spring Boot】GET 映射，返回值自动序列化为 JSON
    public ApiResponse<UserProfile> getProfile(@RequestParam(required = false) Long userId,
                                                Authentication authentication,
                                                @RequestParam String conversationId) {
        userId = CurrentUser.id(authentication);
        log.info(">>> GET /api/profile, userId={}, conversationId={}", userId, conversationId);
        UserProfile profile = profileService.getCurrentProfile(userId, conversationId);
        return ApiResponse.success(profile);
    }
}
