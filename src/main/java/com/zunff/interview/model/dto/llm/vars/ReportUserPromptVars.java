package com.zunff.interview.model.dto.llm.vars;

import cn.hutool.core.bean.BeanUtil;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * report-generator-user 模板变量：键名与 {@code resources/prompts/report-generator-user.prompt} 一致。
 */
@Data
@Builder
public class ReportUserPromptVars {
    private String responseLanguage;
    private String candidateProfile;
    private String jobInfo;
    private int technicalQuestionsDone;
    private int businessQuestionsDone;
    private double technicalAvgScore;
    private double businessAvgScore;
    private double avgScore;
    private double avgAccuracy;
    private double avgLogic;
    private double avgFluency;
    private double avgConfidence;
    private double avgEmotion;
    private double avgBodyLanguage;
    private double avgVoiceTone;
    private int totalQuestions;
    private String technicalRoundScoresText;
    private String businessRoundScoresText;
    private String levelMatchSummary;
    private String followUpChainText;
    private String interviewEndContext;
    private String evalSummary;

    public Map<String, Object> asMap() {
        return BeanUtil.beanToMap(this);
    }
}