package com.zunff.interview.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "开始面试响应")
public class InterviewStartResponse {

    @Schema(description = "面试会话ID", example = "interview-123e4567-e89b-12d3-a456-426614174000")
    private String sessionId;

    @Schema(description = "第一个面试问题")
    private QuestionInfo question;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "问题信息")
    public static class QuestionInfo {

        @Schema(description = "问题内容", example = "请介绍一下你在简历中提到的电商系统架构设计")
        private String content;

        @Schema(description = "问题类型", example = "技术基础", allowableValues = {"技术基础", "项目经验", "技术难点", "系统设计", "业务理解", "场景分析", "沟通协作", "职业素养", "追问"})
        private String type;

        @Schema(description = "问题序号", example = "1")
        private int index;
    }
}
