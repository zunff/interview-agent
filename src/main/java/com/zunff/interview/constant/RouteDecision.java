package com.zunff.interview.constant;

import lombok.Getter;

/**
 * 路由决策枚举
 * 用于状态图中的条件路由
 */
@Getter
public enum RouteDecision {

    FOLLOW_UP("followUp"),
    NEXT_QUESTION("nextQuestion"),
    DEEP_DIVE("deepDive"),          // 低分深入追问
    CHALLENGE_MODE("challengeMode"), // 高分挑战模式
    END("end"),
    TECHNICAL_TO_BUSINESS("technicalToBusiness"),
    BUSINESS_DONE("businessDone"),
    CONTINUE("continue"),
    EARLY_END("earlyEnd");

    private final String value;

    RouteDecision(String value) {
        this.value = value;
    }

    /**
     * 根据值获取路由决策
     */
    public static RouteDecision fromValue(String value) {
        if (value == null) {
            return CONTINUE;
        }
        for (RouteDecision decision : values()) {
            if (decision.value.equals(value)) {
                return decision;
            }
        }
        return CONTINUE;
    }
}