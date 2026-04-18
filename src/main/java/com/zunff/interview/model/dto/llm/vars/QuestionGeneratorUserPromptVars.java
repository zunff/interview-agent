package com.zunff.interview.model.dto.llm.vars;

import cn.hutool.core.bean.BeanUtil;

import java.util.Map;

/**
 * question-generator-user 模板变量
 */
public record QuestionGeneratorUserPromptVars(
        String candidateProfile,
        String jobInfo,
        String roundDisplayName,
        int count,
        String referenceContext,
        String responseLanguage,
        String positionLevel,
        String candidateLevel,
        String difficultyRangeMin,
        String difficultyRangeMax,
        String difficultyPreference
) {
    public Map<String, Object> asMap() {
        return BeanUtil.beanToMap(this);
    }
}
