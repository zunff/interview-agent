package com.zunff.agent.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 提交答案响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewAnswerResponse {

    /** 状态：continue/finished */
    private String status;

    /** 下一个问题（继续时返回） */
    private QuestionInfo question;

    /** 面试报告（结束时返回） */
    private String report;

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

    /**
     * 创建继续响应
     */
    public static InterviewAnswerResponse continueWith(String content, String type, int index) {
        return InterviewAnswerResponse.builder()
                .status("continue")
                .question(QuestionInfo.builder()
                        .content(content)
                        .type(type)
                        .index(index)
                        .build())
                .build();
    }

    /**
     * 创建结束响应
     */
    public static InterviewAnswerResponse finishedWith(String report) {
        return InterviewAnswerResponse.builder()
                .status("finished")
                .report(report)
                .build();
    }
}