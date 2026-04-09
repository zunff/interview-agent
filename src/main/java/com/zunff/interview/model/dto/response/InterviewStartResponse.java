package com.zunff.interview.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开始面试响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewStartResponse {

    /** 会话ID */
    private String sessionId;

    /** 状态 */
    private String status;

    /** 第一个问题 */
    private QuestionInfo question;

    /**
     * 问题信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionInfo {
        /** 问题内容 */
        private String content;

        /** 问题类型 */
        private String type;

        /** 问题序号 */
        private int index;
    }
}
