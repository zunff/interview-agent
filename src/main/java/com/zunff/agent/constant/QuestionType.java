package com.zunff.agent.constant;

import lombok.Getter;

/**
 * 问题类型枚举
 */
@Getter
public enum QuestionType {

    // ========== 技术轮问题类型 ==========
    TECHNICAL_BASIC("技术基础"),
    PROJECT_EXPERIENCE("项目经验"),
    TECHNICAL_CHALLENGE("技术难点"),
    SYSTEM_DESIGN("系统设计"),

    // ========== 业务轮问题类型 ==========
    BUSINESS_UNDERSTANDING("业务理解"),
    COMMUNICATION("沟通协作"),
    PROBLEM_SOLVING("问题解决"),
    PROFESSIONALISM("职业素养"),

    // ========== 通用类型 ==========
    FOLLOW_UP("追问");

    private final String displayName;

    QuestionType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 根据显示名称获取问题类型
     */
    public static QuestionType fromDisplayName(String displayName) {
        if (displayName == null) {
            return TECHNICAL_BASIC;
        }
        for (QuestionType type : values()) {
            if (type.displayName.equals(displayName)) {
                return type;
            }
        }
        return TECHNICAL_BASIC;
    }

    /**
     * 判断是否为技术轮问题类型
     */
    public boolean isTechnicalType() {
        return this == TECHNICAL_BASIC
                || this == PROJECT_EXPERIENCE
                || this == TECHNICAL_CHALLENGE
                || this == SYSTEM_DESIGN;
    }

    /**
     * 判断是否为业务轮问题类型
     */
    public boolean isBusinessType() {
        return this == BUSINESS_UNDERSTANDING
                || this == COMMUNICATION
                || this == PROBLEM_SOLVING
                || this == PROFESSIONALISM;
    }
}