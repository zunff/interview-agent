package com.zunff.interview.constant;

import lombok.Getter;

/**
 * 题目难度枚举
 */
@Getter
public enum Difficulty {
    EASY("easy", "简单"),
    MEDIUM("medium", "中等"),
    HARD("hard", "困难");

    private final String code;
    private final String displayName;

    Difficulty(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public static Difficulty fromCode(String code) {
        if (code == null || code.isBlank()) {
            return MEDIUM;
        }
        for (Difficulty d : values()) {
            if (d.code.equalsIgnoreCase(code)) {
                return d;
            }
        }
        return MEDIUM;
    }
}