package com.zunff.agent.constant;

import lombok.Getter;

/**
 * 面试轮次枚举
 */
@Getter
public enum InterviewRound {

    TECHNICAL("技术轮", "TECHNICAL", "question-generator-technical"),
    BUSINESS("业务轮", "BUSINESS", "question-generator-business");

    private final String displayName;
    private final String code;
    private final String promptTemplate;

    InterviewRound(String displayName, String code, String promptTemplate) {
        this.displayName = displayName;
        this.code = code;
        this.promptTemplate = promptTemplate;
    }

    /**
     * 根据代码获取轮次
     */
    public static InterviewRound fromCode(String code) {
        if (code == null) {
            return TECHNICAL;
        }
        for (InterviewRound round : values()) {
            if (round.code.equals(code)) {
                return round;
            }
        }
        return TECHNICAL;
    }

    /**
     * 是否为技术轮
     */
    public boolean isTechnical() {
        return this == TECHNICAL;
    }

    /**
     * 是否为业务轮
     */
    public boolean isBusiness() {
        return this == BUSINESS;
    }
}