package com.zunff.interview.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 面试历史记录响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "面试历史记录响应")
public class InterviewHistoryResponse {

    @Schema(description = "面试会话ID")
    private String sessionId;

    @Schema(description = "岗位信息")
    private String jobInfo;

    @Schema(description = "会话状态")
    private String status;

    @Schema(description = "当前问题序号")
    private Integer currentQuestionIndex;

    @Schema(description = "技术轮最大问题数")
    private Integer maxTechnicalQuestions;

    @Schema(description = "业务轮最大问题数")
    private Integer maxBusinessQuestions;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;
}
