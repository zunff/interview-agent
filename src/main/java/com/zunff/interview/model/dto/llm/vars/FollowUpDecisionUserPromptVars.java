package com.zunff.interview.model.dto.llm.vars;

import cn.hutool.core.bean.BeanUtil;

import java.util.Map;

/**
 * followup-decision-user 模板变量：键名与 prompt 模板占位符保持一致。
 */
public record FollowUpDecisionUserPromptVars(
        String question,
        String answer,
        int overallScore,
        int accuracy,
        int logic,
        int fluency,
        int confidence,
        String strengths,
        String weaknesses,
        String detailedEvaluation,
        int emotionScore,
        int bodyLanguageScore,
        int voiceToneScore,
        boolean modalityConcern,
        String modalityFollowUpSuggestion,
        int followUpCount,
        int maxFollowUps,
        String responseLanguage
) {
    public Map<String, Object> asMap() {
        return BeanUtil.beanToMap(this);
    }
}
