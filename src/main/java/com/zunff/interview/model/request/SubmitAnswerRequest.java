package com.zunff.interview.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交答案请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "提交答案请求")
public class SubmitAnswerRequest {

    @Schema(description = "面试会话ID", example = "interview-123e4567-e89b-12d3-a456-426614174000")
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    @Schema(description = "回答文本内容（可选，不传则使用服务端语音转写）", example = "我之前的项目主要使用Spring Boot框架，对于高并发场景，我们采用了Redis缓存...")
    private String answerText;

    @Schema(description = "回答音频数据（Base64编码）")
    private String answerAudio;

    @Schema(description = "关键帧数据（Base64编码，逗号分隔，内部使用）", hidden = true)
    private String videoFrames;
}
