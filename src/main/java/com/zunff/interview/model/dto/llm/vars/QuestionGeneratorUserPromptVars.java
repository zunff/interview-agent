package com.zunff.interview.model.dto.llm.vars;

import cn.hutool.core.bean.BeanUtil;

import java.util.Map;

/**
 * question-generator-user 模板变量：键名与 prompt 模板占位符保持一致。
 */
public record QuestionGeneratorUserPromptVars(
        String candidateProfile,
        String jobInfo,
        String roundDisplayName,
        String progressLabel,
        int doneCount,
        int maxCount,
        int questionIndex,
        boolean hasPreviousQuestions,
        String previousQuestions,
        String firstQuestionHint,
        String referenceContext,
        String responseLanguage,
        String positionLevel,
        String candidateLevel,
        String matchScore,
        String difficultyRangeMin,
        String difficultyRangeMax,
        String difficultyPreference
) {
    public Map<String, Object> asMap() {
        return BeanUtil.beanToMap(this);
    }
}
