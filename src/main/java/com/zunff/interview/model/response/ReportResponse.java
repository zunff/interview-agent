package com.zunff.interview.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 面试报告响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "面试报告响应")
public class ReportResponse {

    @Schema(description = "面试会话ID", example = "interview-123e4567-e89b-12d3-a456-426614174000")
    private String sessionId;

    @Schema(description = "面试报告内容（Markdown格式）")
    private String report;

    @Schema(description = "会话状态", example = "completed")
    private String status;
}
