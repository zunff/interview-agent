package com.zunff.interview.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应包装类
 *
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 响应码 */
    private int code;

    /** 响应消息 */
    private String message;

    /** 响应数据 */
    private T data;

    /** 时间戳 */
    private long timestamp;

    /**
     * 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, System.currentTimeMillis());
    }

    /**
     * 成功响应（无数据）
     */
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(200, "success", null, System.currentTimeMillis());
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data, System.currentTimeMillis());
    }

    /**
     * 错误响应
     */
    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null, System.currentTimeMillis());
    }

    /**
     * 错误响应（使用错误码枚举）
     */
    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null, System.currentTimeMillis());
    }

    /**
     * 错误码枚举
     */
    public enum ErrorCode {
        /** 参数错误 */
        BAD_REQUEST(400, "参数错误"),
        /** 未授权 */
        UNAUTHORIZED(401, "未授权"),
        /** 禁止访问 */
        FORBIDDEN(403, "禁止访问"),
        /** 资源不存在 */
        NOT_FOUND(404, "资源不存在"),
        /** 内部服务器错误 */
        INTERNAL_ERROR(500, "服务器内部错误"),
        /** 会话不存在 */
        SESSION_NOT_FOUND(1001, "面试会话不存在"),
        /** 会话已结束 */
        SESSION_ENDED(1002, "面试会话已结束"),
        /** 面试启动失败 */
        INTERVIEW_START_FAILED(1003, "面试启动失败"),
        /** 答案提交失败 */
        ANSWER_SUBMIT_FAILED(1004, "答案提交失败"),
        /** 报告生成失败 */
        REPORT_GENERATE_FAILED(1005, "报告生成失败");

        private final int code;
        private final String message;

        ErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
