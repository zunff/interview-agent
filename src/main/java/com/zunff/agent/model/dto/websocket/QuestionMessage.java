package com.zunff.agent.model.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 问题消息载体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionMessage {

    /** 问题内容 */
    private String content;

    /** 问题类型 */
    private String questionType;

    /** 问题序号 */
    private int questionIndex;

    /** 是否追问 */
    private boolean isFollowUp;
}
