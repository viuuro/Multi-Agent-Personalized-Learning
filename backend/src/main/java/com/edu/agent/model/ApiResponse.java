package com.edu.agent.model;

/**
 * 统一 API 响应包装类
 *
 * 所有后端接口返回此格式，确保前端能统一处理：
 *   - code: HTTP 状态码（200 成功，其他为错误码）
 *   - message: 提示信息
 *   - data: 业务数据（泛型，可以是任意类型）
 *
 * 使用方式：
 *   ApiResponse.success(data)       → 返回 200 + 数据
 *   ApiResponse.error(400, "xxx")   → 返回错误码 + 错误信息
 */
public class ApiResponse<T> {

    /** 状态码，200 表示成功 */
    private int code;

    /** 提示信息，成功时为 "ok"，失败时为具体错误描述 */
    private String message;

    /** 响应数据，泛型类型，失败时为 null */
    private T data;

    public ApiResponse() {}

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 快捷方法：返回成功响应（默认消息 "ok"） */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "ok", data);
    }

    /** 快捷方法：返回成功响应（自定义消息） */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    /** 快捷方法：返回错误响应 */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    // ========== Getter / Setter ==========

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
