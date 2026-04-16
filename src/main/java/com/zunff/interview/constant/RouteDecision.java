package com.zunff.interview.constant;

import lombok.Getter;

/**
 * 路由决策枚举
 * 仅用于子图级别的条件路由（追问策略 + 轮次完成）
 */
@Getter
public enum RouteDecision {

    FOLLOW_UP("followUp"),
    NEXT_QUESTION("nextQuestion"),
    DEEP_DIVE("deepDive"),           // 低分深入追问
    CHALLENGE_MODE("challengeMode"),  // 高分挑战模式
    ROUND_COMPLETE("roundComplete");  // 轮次结束

    private final String value;

    RouteDecision(String value) {
        this.value = value;
    }

    /**
     * 根据值获取路由决策
     */
    public static RouteDecision fromValue(String value) {
        if (value == null) {
            return NEXT_QUESTION;
        }
        for (RouteDecision decision : values()) {
            if (decision.value.equals(value)) {
                return decision;
            }
        }
        return NEXT_QUESTION;
    }
}
