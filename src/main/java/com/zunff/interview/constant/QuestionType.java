package com.zunff.interview.constant;

import lombok.Getter;

/**
 * 问题类型枚举
 */
@Getter
public enum QuestionType {

    // ========== 技术轮问题类型 ==========
    TECHNICAL_BASIC(1, "technical_basic", "技术基础", "technical"),
    PROJECT_EXPERIENCE(2, "project_experience", "项目经验", "project"),
    TECHNICAL_CHALLENGE(3, "technical_challenge", "技术难点", "technical"),
    SYSTEM_DESIGN(4, "system_design", "系统设计", "technical"),

    // ========== 业务轮问题类型 ==========
    BUSINESS_UNDERSTANDING(5, "business_understanding", "业务理解", "business"),
    COMMUNICATION(6, "communication", "沟通协作", "soft"),
    PROBLEM_SOLVING(7, "problem_solving", "场景分析", "business"),
    PROFESSIONALISM(8, "professionalism", "职业素养", "soft"),

    // ========== 追问类型 ==========
    FOLLOW_UP(9, "follow_up", "追问", "followup-basic"),
    CHALLENGE_QUESTION(10, "challenge_question", "挑战题", "followup-challenge"),
    DEEP_DIVE(11, "deep_dive", "深入追问", "followup-deep-dive"),
    ;

    private final int code;
    private final String apiName;
    private final String displayName;
    private final String evaluationPrompt;

    QuestionType(int code, String apiName, String displayName, String evaluationPrompt) {
        this.code = code;
        this.apiName = apiName;
        this.displayName = displayName;
        this.evaluationPrompt = evaluationPrompt;
    }

    /**
     * 根据显示名称获取问题类型
     */
    public static QuestionType fromDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return TECHNICAL_BASIC;
        }
        String normalized = displayName.trim().toLowerCase().replace('-', '_');
        for (QuestionType type : values()) {
            if (type.displayName.equals(displayName)
                    || type.apiName.equalsIgnoreCase(normalized)
                    || type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return TECHNICAL_BASIC;
    }

    public static QuestionType fromCode(Integer code) {
        if (code == null) {
            return TECHNICAL_BASIC;
        }
        for (QuestionType type : values()) {
            if (type.code == code) {
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

    /**
     * 判断是否为追问类型
     */
    public boolean isFollowUpType() {
        return this == FOLLOW_UP
                || this == CHALLENGE_QUESTION
                || this == DEEP_DIVE;
    }
}