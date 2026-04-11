package com.zunff.interview.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交答案响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "提交答案响应")
public class InterviewAnswerResponse {

    @Schema(description = "面试状态", example = "continue", allowableValues = {"continue", "finished"})
    private String status;

    @Schema(description = "下一个问题（状态为continue时返回）")
    private QuestionInfo question;

    @Schema(description = "面试报告（状态为finished时返回）")
    private String report;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "问题信息")
    public static class QuestionInfo {

        @Schema(description = "问题内容", example = "你在项目中是如何处理分布式事务的？")
        private String content;

        @Schema(description = "问题类型", example = "技术难点")
        private String type;

        @Schema(description = "问题序号", example = "2")
        private int index;
    }

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

    public static InterviewAnswerResponse finishedWith(String report) {
        return InterviewAnswerResponse.builder()
                .status("finished")
                .report(report)
                .build();
    }
}
