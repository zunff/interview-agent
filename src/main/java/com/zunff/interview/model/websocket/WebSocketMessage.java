package com.zunff.interview.model.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 消息包装类
 *
 * @param <T> 消息载体类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage<T> {

    /** 消息类型 */
    private String type;

    /** 消息载体 */
    private T payload;

    /** 时间戳 */
    private long timestamp;

    /**
     * 创建消息
     */
    public static <T> WebSocketMessage<T> of(String type, T payload) {
        return WebSocketMessage.<T>builder()
                .type(type)
                .payload(payload)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 消息类型常量
     */
    public static class Type {
        /** 新问题 */
        public static final String NEW_QUESTION = "new_question";
        /** 评估结果 */
        public static final String EVALUATION_RESULT = "evaluation_result";
        /** 最终报告 */
        public static final String FINAL_REPORT = "final_report";
        /** 回答已接收 */
        public static final String ANSWER_RECEIVED = "answer_received";
        /** 错误消息 */
        public static final String ERROR = "error";
        /** 语音问题开始 */
        public static final String AUDIO_QUESTION_START = "audio_question_start";
        /** 语音问题音频块 */
        public static final String AUDIO_QUESTION_CHUNK = "audio_question_chunk";
        /** 语音问题结束 */
        public static final String AUDIO_QUESTION_END = "audio_question_end";
        /** 语音问题错误 */
        public static final String AUDIO_QUESTION_ERROR = "audio_question_error";
        /** 会话创建成功 */
        public static final String SESSION_CREATED = "session_created";
        /** 自我介绍阶段信号 */
        public static final String SELF_INTRO = "self_intro";
        /** 岗位分析完成信号 */
        public static final String JOB_ANALYSIS_COMPLETE = "job_analysis_complete";
    }
}
