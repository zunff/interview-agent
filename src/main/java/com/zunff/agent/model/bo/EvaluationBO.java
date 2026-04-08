package com.zunff.agent.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 评估结果业务对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationBO {

    /** 问题序号 */
    private int questionIndex;

    /** 问题内容 */
    private String question;

    /** 回答内容 */
    private String answer;

    /** 内容准确性得分 (0-100) */
    private int accuracy;

    /** 逻辑清晰度得分 (0-100) */
    private int logic;

    /** 表达流畅度得分 (0-100) */
    private int fluency;

    /** 自信程度得分 (0-100) */
    private int confidence;

    /** 视频情感得分 (0-100) */
    private int emotionScore;

    /** 肢体语言得分 (0-100) */
    private int bodyLanguageScore;

    /** 语音语调得分 (0-100) */
    private int voiceToneScore;

    /** 综合得分 (0-100) */
    private int overallScore;

    /** 优点列表 */
    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    /** 不足列表 */
    @Builder.Default
    private List<String> weaknesses = new ArrayList<>();

    /** 详细评价 */
    private String detailedEvaluation;

    /** 是否需要追问 */
    private boolean needFollowUp;

    /** 追问建议 */
    private String followUpSuggestion;
}
