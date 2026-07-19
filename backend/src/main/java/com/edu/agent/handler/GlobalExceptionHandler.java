package com.edu.agent.handler;

import com.edu.agent.model.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理器 —— 统一返回 ApiResponse 格式的错误响应
 *
 * 捕获控制器层抛出的各种异常，转换为统一的 JSON 错误格式返回给前端。
 * 前端可以根据 code 和 message 字段统一处理错误提示。
 *
 * ========== 异常处理策略 ==========
 *   IllegalArgumentException → 400 （业务异常，如任务不存在、无权限）
 *   IllegalAccessException  → 403 （权限异常）
 *   RuntimeException        → 500 （运行时异常，记录完整堆栈）
 *   Exception               → 500 （兜底，防止未捕获异常泄漏）
 *
 * ========== 返回格式示例 ==========
 *   {
 *     "code": 400,
 *     "message": "任务不存在或无权访问",
 *     "data": null
 *   }
 *
 * ========== 在评分模块中的使用 ==========
 *   SubmissionController 中的业务校验抛出 IllegalArgumentException：
 *     - "任务不存在或无权访问" — POST /api/submissions 时任务 ID 无效
 *     - "提交记录不存在或无权访问" — GET /api/submissions/{id} 时 ID 无效
 *     - "提交内容不能为空" — POST content 为空
 *   SubmissionController 不捕获这些异常，由 GlobalExceptionHandler 统一处理。
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @RestControllerAdvice: Spring MVC 全局异常处理注解
 *   - @ExceptionHandler: 指定要处理的异常类型
 *   - ResponseEntity: Spring 提供的 HTTP 响应实体，可自定义状态码
 */
@RestControllerAdvice  // 【Spring Boot】全局异常拦截器（对所有 @RestController 生效）
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Preserve controller-selected HTTP statuses and their safe user-facing reasons. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = "请求处理失败";
        }
        log.warn("请求状态异常: status={}, reason={}", status, message);
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.error(status, message));
    }

    /**
     * 处理业务异常（如任务不存在、无权限等）
     *
     * 当 SubmissionService 中校验失败时抛出 IllegalArgumentException，
     * 前端收到 400 状态码 + 中文错误描述，可直接展示给用户。
     *
     * @param ex IllegalArgumentException 异常对象
     * @return 400 状态码 + ApiResponse 错误格式
     */
    @ExceptionHandler(IllegalArgumentException.class)  // 【Spring Boot】拦截 IllegalArgumentException
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("业务异常: {}", ex.getMessage());  // 记录警告日志，不打印堆栈
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)           // 400
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    /**
     * 处理权限异常
     *
     * @param ex IllegalAccessException 异常对象
     * @return 403 状态码 + 错误描述
     */
    @ExceptionHandler(IllegalAccessException.class)  // 【Spring Boot】拦截 IllegalAccessException
    public ResponseEntity<ApiResponse<Void>> handleIllegalAccess(IllegalAccessException ex) {
        log.warn("权限异常: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)             // 403
                .body(ApiResponse.error(403, ex.getMessage()));
    }

    /**
     * 处理其他未预期的运行时异常
     *
     * 这类异常通常表示程序 bug 或系统故障（如数据库连接失败、空指针等），
     * 打印完整堆栈以便排查问题。
     *
     * @param ex RuntimeException 异常对象
     * @return 500 状态码 + 通用错误消息
     */
    @ExceptionHandler(RuntimeException.class)  // 【Spring Boot】拦截 RuntimeException
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException ex) {
        log.error("运行时异常: ", ex);  // 记录错误日志并打印堆栈
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // 500
                .body(ApiResponse.error(500, "服务器内部错误"));
    }

    /**
     * 处理兜底异常 —— 捕获所有未被上述处理器捕获的异常
     *
     * 防止任何未预期的异常泄漏到客户端，统一返回 500 + 通用错误消息。
     *
     * @param ex Exception 异常对象
     * @return 500 状态码 + 通用错误消息
     */
    @ExceptionHandler(Exception.class)  // 【Spring Boot】拦截所有 Exception（兜底）
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("未预期异常: ", ex);  // 记录错误日志并打印堆栈
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // 500
                .body(ApiResponse.error(500, "服务器内部错误"));
    }
}
