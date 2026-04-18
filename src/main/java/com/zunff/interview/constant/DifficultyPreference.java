package com.zunff.interview.constant;

import lombok.Getter;

/**
 * 难度偏好枚举
 */
@Getter
public enum DifficultyPreference {
    FUNDAMENTALS_FOCUSED("fundamentals_focused", "侧重基础"),
    SLIGHTLY_LOWERED("slightly_lowered", "适当降低"),
    STANDARD("standard", "标准难度"),
    MODERATE_CHALLENGE("moderate_challenge", "适度挑战"),
    MOSTLY_HARD("mostly_hard", "高难度为主"),
    MAXIMUM_DIFFICULTY("maximum_difficulty", "最高难度");

    private final String code;
    private final String displayName;

    DifficultyPreference(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public static DifficultyPreference fromCode(String code) {
        if (code == null || code.isBlank()) {
            return STANDARD;
        }
        for (DifficultyPreference p : values()) {
            if (p.code.equalsIgnoreCase(code)) {
                return p;
            }
        }
        return STANDARD;
    }
}