package com.zunff.interview.model.bo.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 带时间戳的视频帧
 * 用于 Omni 多模态综合评估时的帧-语音时间对齐
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrameWithTimestamp implements Serializable {

    /**
     * Base64 编码的视频帧
     */
    private String frame;

    /**
     * 前端传入的 UTC 时间戳（毫秒）
     */
    private long timestampMs;
}