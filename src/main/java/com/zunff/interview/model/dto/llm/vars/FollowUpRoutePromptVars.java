package com.zunff.interview.model.dto.llm.vars;

import cn.hutool.core.bean.BeanUtil;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * followup-route-decision 模板变量
 */
@Data
@Builder
public class FollowUpRoutePromptVars {
    private String responseLanguage;
    private int overallScore;
    private int accuracy;
    private int logic;
    private int fluency;
    private int confidence;
    private int emotionScore;
    private int bodyLanguageScore;
    private int voiceToneScore;
    private boolean modalityConcern;
    private String question;
    private String answer;
    private String difficulty;
    private String expectedKeywords;
    private String questionIntent;
    private String strengths;
    private String weaknesses;
    private String detailedEvaluation;
    private int followUpCount;
    private int maxFollowUps;
    private int remainingFollowUps;

    public Map<String, Object> asMap() {
        return BeanUtil.beanToMap(this);
    }
}