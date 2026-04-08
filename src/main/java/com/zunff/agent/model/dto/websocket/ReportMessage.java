package com.zunff.agent.model.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报告消息载体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportMessage {

    /** 报告内容 */
    private String report;
}