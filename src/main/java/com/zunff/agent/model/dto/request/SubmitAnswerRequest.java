package com.zunff.agent.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 提交答案请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitAnswerRequest {

    /** 面试会话ID */
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    /** 回答文本（语音转写结果） */
    @NotBlank(message = "回答内容不能为空")
    private String answerText;

    /** 回答音频（Base64编码） */
    private String answerAudio;

    /** 回答视频帧（Base64编码列表） */
    private List<String> answerFrames;
}
