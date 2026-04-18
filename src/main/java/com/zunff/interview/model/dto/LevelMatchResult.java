package com.zunff.interview.model.dto;

import java.io.Serial;
import java.io.Serializable;

/**
 * 级别匹配结果
 * 纯数据容器，由 ProfileAnalysisNode 从 LLM 返回中构建
 */
public record LevelMatchResult(
        JobAnalysisResult.PositionLevel positionLevel,
        JobAnalysisResult.PositionLevel candidateLevel,
        double matchScore,
        String difficultyRangeMin,
        String difficultyRangeMax,
        String difficultyPreference,
        String matchReason
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 获取难度范围描述
     */
    public String getDifficultyRange() {
        return difficultyRangeMin + "-" + difficultyRangeMax;
    }

    /**
     * 判断是否级别不匹配
     */
    public boolean isLevelMismatch() {
        return positionLevel != candidateLevel;
    }

    /**
     * 判断候选人级别是否低于岗位级别
     */
    public boolean isCandidateLower() {
        return candidateLevel.getCode() < positionLevel.getCode();
    }
}