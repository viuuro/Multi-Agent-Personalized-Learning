package com.edu.agent.controller;

import com.edu.agent.controller.dto.SubmissionDetail;
import com.edu.agent.controller.dto.SubmissionRequest;
import com.edu.agent.model.ApiResponse;
import com.edu.agent.service.SubmissionService;
import com.edu.agent.service.RequestRateLimiter;
import com.edu.agent.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务成果提交与 AI 评价控制器 —— 任务评分模块的 REST API 入口
 *
 * ========== 提供以下接口 ==========
 *   POST   /api/submissions                 提交任务成果，异步触发 AI 评分
 *   GET    /api/submissions/{id}            查看提交详情及 AI 评价结果
 *   GET    /api/tasks/{taskId}/submissions  查看某个任务的所有提交（按时间倒序）
 *
 * ========== 鉴权说明 ==========
 *   项目使用 Spring Security Session 认证，用户 ID 仅从服务端认证会话读取。
 *   所有接口都会校验用户只能查看/提交属于自己的数据，客户端用户 ID 不参与鉴权。
 *
 * ========== 接口调用示例 ==========
 *
 *   1. 提交成果：
 *      curl -X POST http://localhost:8080/api/submissions \
 *        -H "Content-Type: application/json" \
 *        -b cookies.txt \
 *        -d '{"taskId": 1, "content": "这是我完成本次任务的成果..."}'
 *      → 返回：{ "code":200, "data":{ "submissionId":1 }, "message":"提交成功，AI 正在评价中" }
 *
 *   2. 查询评价结果：
 *      curl http://localhost:8080/api/submissions/1 -b cookies.txt
 *      → 返回提交内容 + AI 评分/分析/建议（若 AI 尚未完成，evaluation 为 null）
 *
 *   3. 查询任务所有提交：
 *      curl http://localhost:8080/api/tasks/1/submissions -b cookies.txt
 *      → 返回该任务的所有提交记录列表（按时间倒序）
 *
 * ========== 异常处理 ==========
 *   业务异常（任务不存在、无权限等）由 GlobalExceptionHandler 统一拦截，
 *   返回统一的 {code:400, message:"错误描述", data:null} 格式。
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @RestController: 声明为 REST 控制器，自动序列化返回值为 JSON
 *   - @RequestMapping("/api"): 为所有接口添加 /api 前缀
 *   - @PostMapping / @GetMapping: 映射 HTTP 方法和路径
 *   - @RequestBody: 将请求体 JSON 自动反序列化为 Java 对象
 *   - @PathVariable: 从 URL 路径中提取参数
 *   - Authentication: 从 Spring Security 会话中读取认证主体
 *   - 构造器注入: SubmissionService 由 Spring 自动装配
 */
@RestController  // 【Spring Boot】声明为 REST 控制器，自动处理 JSON 序列化
@RequestMapping("/api")  // 【Spring Boot】为类中所有方法添加 /api 路径前缀
public class SubmissionController {

    private static final Logger log = LoggerFactory.getLogger(SubmissionController.class);

    /** 任务提交与 AI 评价业务服务 */
    private final SubmissionService submissionService;
    private final RequestRateLimiter rateLimiter;

    /** 【Spring Boot】构造器注入 */
    public SubmissionController(SubmissionService submissionService, RequestRateLimiter rateLimiter) {
        this.submissionService = submissionService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * POST /api/submissions —— 提交任务成果
     *
     * 用户完成任务后，通过此接口提交成果内容（文本描述、代码片段、链接等）。
     * 系统会在后台异步调用 AI 进行评分和分析。
     *
     * ========== 请求格式 ==========
     *   请求凭证: 已登录的 Session Cookie
     *   请求体: {
     *     "taskId": 1,                （任务 ID，必填）
     *     "content": "成果内容..."     （提交内容，必填，不能为空）
     *   }
     *
     * ========== 响应格式 ==========
     *   成功：{ "code":200, "message":"提交成功，AI 正在评价中", "data":{ "submissionId":1 } }
     *   失败：{ "code":400, "message":"taskId 不能为空", "data":null }
     *
     * ========== 后续操作 ==========
     *   前端收到 submissionId 后，可以：
     *   - 立即通过 GET /api/submissions/{id} 查询（此时 status=PENDING，evaluation=null）
     *   - 轮询等待 AI 评价完成（status→EVALUATED，evaluation 有值）
     *   - 或等几秒后刷新页面查看结果
     *
     * @param body   包含 taskId 和 content 的请求体（JSON 自动反序列化）
     * @param authentication Spring Security 当前认证会话
     * @return 包含 submissionId 的成功响应，或错误响应
     */
    @PostMapping("/submissions")  // 【Spring Boot】POST 映射
    public ApiResponse<Map<String, Object>> submit(@RequestBody SubmissionRequest body,
                                                   Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        if (!rateLimiter.tryAcquire("submission:" + userId, 10, 60)) {
            return ApiResponse.error(429, "成果提交过于频繁，请稍后再试");
        }
        // 参数校验
        if (body.getTaskId() == null
                && (body.getConversationId() == null || body.getConversationId().isBlank())) {
            return ApiResponse.error(400, "taskId 和 conversationId 至少提供一个");
        }
        if (body.getContent() == null || body.getContent().trim().isEmpty()) {
            return ApiResponse.error(400, "content 不能为空");
        }

        log.info(">>> POST /api/submissions —— userId={}, taskId={}, conversationId={}",
                userId, body.getTaskId(), body.getConversationId());

        try {
            Long submissionId = body.getConversationId() != null && !body.getConversationId().isBlank()
                    ? submissionService.submit(userId, body.getConversationId(), body.getFileName(),
                            body.getFileSize(), body.getWeekNumber(), body.getTaskIndex(), body.getContent())
                    : submissionService.submit(userId, body.getTaskId(), body.getContent());
            return ApiResponse.success("提交成功，AI 正在评价中", Map.of("submissionId", submissionId));
        } catch (IllegalArgumentException e) {
            // 业务异常（如任务不存在、无权限）返回 400 错误
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** GET /api/conversations/{conversationId}/submissions —— 恢复对话内成果与评分。 */
    @GetMapping("/conversations/{conversationId}/submissions")
    public ApiResponse<List<SubmissionDetail>> getConversationSubmissions(
            @PathVariable String conversationId,
            Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        try {
            return ApiResponse.success(submissionService
                    .getSubmissionsByConversation(userId, conversationId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * GET /api/submissions/{id} —— 查看提交详情及 AI 评价
     *
     * 返回用户提交的原始内容以及 AI 的评分、分析和改进建议。
     *
     * ========== 不同状态下的返回值 ==========
     *   status=PENDING   → evaluation 字段为 null（AI 尚未完成评价）
     *   status=EVALUATED → evaluation 包含完整的 AI 评分/分析/建议
     *   status=ERROR     → evaluation 为 null，errorMessage 包含失败原因
     *
     * ========== 响应格式示例（status=EVALUATED） ==========
     *   {
     *     "code": 200,
     *     "data": {
     *       "id": 1,
     *       "taskId": 1,
     *       "userId": 1,
     *       "content": "成果内容...",
     *       "submissionTime": "2024-01-01T10:00:00",
     *       "status": "EVALUATED",
     *       "errorMessage": null,
     *       "evaluation": {
     *         "id": 1,
     *         "score": 85,
     *         "analysis": "详细分析...",
     *         "suggestion": "改进建议...",
     *         "evaluationTime": "2024-01-01T10:00:30"
     *       }
     *     }
     *   }
     *
     * @param id     提交记录 ID（URL 路径参数）
     * @param userId 当前用户 ID（从 Header 获取，用于权限校验）
     * @return 提交详情（含 AI 评价）
     */
    @GetMapping("/submissions/{id}")  // 【Spring Boot】GET 映射，{id} 为路径变量
    public ApiResponse<SubmissionDetail> getSubmission(@PathVariable Long id,
                                                       Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        log.info(">>> GET /api/submissions/{} —— userId={}", id, userId);

        try {
            SubmissionDetail detail = submissionService.getSubmissionDetail(id, userId);
            return ApiResponse.success(detail);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * GET /api/tasks/{taskId}/submissions —— 查看某个任务的所有提交（按时间倒序）
     *
     * 返回该任务下的所有提交记录列表，每条记录附带对应的 AI 评价结果（如果有）。
     * 按提交时间倒序排列，最新的提交排在最前面。
     *
     * @param taskId 任务 ID（URL 路径参数）
     * @param userId 当前用户 ID（从 Header 获取，用于权限校验）
     * @return 提交详情列表
     */
    @GetMapping("/tasks/{taskId}/submissions")  // 【Spring Boot】GET 映射
    public ApiResponse<List<SubmissionDetail>> getTaskSubmissions(@PathVariable Long taskId,
                                                                   Authentication authentication) {
        Long userId = CurrentUser.id(authentication);
        log.info(">>> GET /api/tasks/{}/submissions —— userId={}", taskId, userId);

        try {
            List<SubmissionDetail> details = submissionService.getSubmissionsByTask(taskId, userId);
            return ApiResponse.success(details);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
