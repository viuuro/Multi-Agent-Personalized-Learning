package com.edu.agent.controller;

import com.edu.agent.model.ApiResponse;
import com.edu.agent.model.User;
import com.edu.agent.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证控制器 —— 提供用户登录、注册、账号管理接口
 *
 * 核心接口：
 *   POST /api/auth/login     — 用户登录（验证用户名+密码）
 *   POST /api/auth/register  — 用户注册（创建新账号）
 *   PUT  /api/auth/profile   — 更新用户资料（修改用户名/密码/头像）
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @RestController: 声明为 REST 控制器，返回值自动序列化为 JSON
 *   - @RequestMapping("/api/auth"): 为所有接口添加 /api/auth 前缀
 *   - @PostMapping / @PutMapping: 映射 HTTP 方法和路径
 *   - @RequestBody: 将 HTTP 请求体中的 JSON 自动反序列化为 Java 对象
 *   - 构造器注入: AuthService 由 Spring 自动装配
 */
@RestController  // 【Spring Boot】声明为 REST 控制器
@RequestMapping("/api/auth")  // 【Spring Boot】为所有接口添加 /api/auth 前缀
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;  // 【Spring Boot】构造器注入的认证服务

    /** 【Spring Boot】构造器注入 —— Spring 自动解析并注入 AuthService Bean */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login —— 用户登录
     *
     * 输入：JSON { "username": "用户名", "password": "密码" }
     * 输出：ApiResponse<{ id, username, avatar }>
     *
     * 成功返回 200 + 用户信息，失败返回 401 + 错误原因
     */
    @PostMapping("/login")  // 【Spring Boot】POST 映射
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        // 【Spring Boot】@RequestBody 将 JSON 请求体自动反序列化为 Map
        String username = body.get("username");
        String password = body.get("password");
        try {
            // 调用 AuthService 验证用户名和密码
            User user = authService.login(username, password);
            // 构造返回数据（不返回密码等敏感信息）
            Map<String, Object> data = Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "avatar", user.getAvatar() != null ? user.getAvatar() : ""
            );
            return ApiResponse.success("登录成功", data);
        } catch (RuntimeException e) {
            // 登录失败返回 401 未授权
            return ApiResponse.error(401, e.getMessage());
        }
    }

    /**
     * POST /api/auth/register —— 用户注册
     *
     * 输入：JSON { "username": "用户名", "password": "密码" }
     * 输出：ApiResponse<{ id, username, avatar }>
     *
     * 成功返回 200 + 用户信息，失败返回 400 + 错误原因
     */
    @PostMapping("/register")  // 【Spring Boot】POST 映射
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        try {
            // 调用 AuthService 创建新用户
            User user = authService.register(username, password);
            Map<String, Object> data = Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "avatar", ""  // 新用户无头像
            );
            return ApiResponse.success("注册成功", data);
        } catch (RuntimeException e) {
            // 注册失败返回 400 错误请求
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * DELETE /api/auth/account —— 注销账号
     *
     * 输入：JSON { "userId": "1", "password": "密码" }
     * 输出：ApiResponse<null>
     *
     * 验证密码后，删除用户及其所有关联数据（对话记录、画像数据）
     */
    @DeleteMapping("/account")  // 【Spring Boot】DELETE 映射
    public ApiResponse<Void> deleteAccount(@RequestBody Map<String, String> body) {
        try {
            Long userId = Long.parseLong(body.get("userId"));
            String password = body.get("password");
            authService.deleteUser(userId, password);
            return ApiResponse.success("账号已注销", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * PUT /api/auth/profile —— 更新用户资料
     *
     * 输入：JSON { "userId": "1", "username": "新用户名", "currentPassword": "原密码", "password": "新密码", "avatar": "Base64数据" }
     * 输出：ApiResponse<{ id, username, avatar }>
     *
     * 所有字段均可选，只传需要修改的字段即可
     */
    @PutMapping("/profile")  // 【Spring Boot】PUT 映射（用于更新操作）
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        try {
            Long userId = Long.parseLong(body.get("userId"));
            String username = body.get("username");
            String currentPassword = body.get("currentPassword");
            String password = body.get("password");
            String avatar = body.get("avatar");
            // 调用 AuthService 更新用户资料
            User user = authService.updateProfile(userId, username, currentPassword, password, avatar);
            Map<String, Object> data = Map.of(
                    "id", user.getId(),
                    "username", user.getUsername(),
                    "avatar", user.getAvatar() != null ? user.getAvatar() : ""
            );
            return ApiResponse.success("更新成功", data);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
