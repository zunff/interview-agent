package com.zunff.interview.model.dto.llm.vars;

import cn.hutool.core.bean.BeanUtil;

import java.util.Map;

/**
 * report-generator-user 模板变量：键名与 {@code resources/prompts/report-generator-user.prompt} 一致。
 */
public record ReportUserPromptVars(
        String candidateProfile,
        String jobInfo,
        int technicalQuestionsDone,
        int businessQuestionsDone,
        double technicalAvgScore,
        double businessAvgScore,
        double avgScore,
        double avgAccuracy,
        double avgLogic,
        double avgFluency,
        double avgConfidence,
        double avgEmotion,
        double avgBodyLanguage,
        double avgVoiceTone,
        int totalQuestions,
        String evalSummary
) {
    public Map<String, Object> asMap() {
        return BeanUtil.beanToMap(this);
    }

}