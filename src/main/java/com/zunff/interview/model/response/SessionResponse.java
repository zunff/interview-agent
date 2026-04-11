package com.zunff.interview.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话状态响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会话状态响应")
public class SessionResponse {

    @Schema(description = "面试会话ID", example = "interview-123e4567-e89b-12d3-a456-426614174000")
    private String sessionId;

    @Schema(description = "会话状态", example = "in_progress", allowableValues = {"created", "in_progress", "waiting_answer", "completed"})
    private String status;

    @Schema(description = "当前问题序号", example = "3")
    private int currentQuestionIndex;

    @Schema(description = "会话创建时间", example = "2026-04-09T10:30:00")
    private LocalDateTime createTime;
}
