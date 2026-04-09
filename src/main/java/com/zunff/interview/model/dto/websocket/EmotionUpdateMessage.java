package com.zunff.interview.model.dto.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 情感更新消息载体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmotionUpdateMessage {

    /** 情感得分 */
    private int emotionScore;

    /** 肢体语言得分 */
    private int bodyLanguageScore;
}
