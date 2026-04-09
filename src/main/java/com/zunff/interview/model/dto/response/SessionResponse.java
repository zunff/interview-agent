package com.zunff.interview.model.dto.response;

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
public class SessionResponse {

    /** 会话ID */
    private String sessionId;

    /** 会话状态 */
    private String status;

    /** 当前问题序号 */
    private int currentQuestionIndex;

    /** 创建时间 */
    private LocalDateTime createTime;
}
