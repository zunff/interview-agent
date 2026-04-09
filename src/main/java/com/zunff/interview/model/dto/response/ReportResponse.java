package com.zunff.interview.model.dto.response;

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
public class ReportResponse {

    /** 会话ID */
    private String sessionId;

    /** 面试报告内容 */
    private String report;

    /** 会话状态 */
    private String status;
}
