package com.zunff.interview.model.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 转录条目
 * 记录每句话的文本和绝对时间戳
 * 绝对时间戳 = 前端传入的开始时间戳 + ASR返回的相对时间
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 转录文本 */
    private String text;

    /** 绝对开始时间戳（ms） */
    private long startTimeMs;

    /** 绝对结束时间戳（ms） */
    private long endTimeMs;
}
