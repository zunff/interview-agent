package com.zunff.agent.constant;

import lombok.Getter;

/**
 * 路由决策枚举
 * 用于状态图中的条件路由
 */
@Getter
public enum RouteDecision {

    FOLLOW_UP("followUp"),
    NEXT_QUESTION("nextQuestion"),
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