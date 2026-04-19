package com.zunff.interview.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 面试题目业务对象
 * 包含题目元信息、标准答案、建议和评估结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestionBO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========== 题目元信息 ==========
    /** 题目内容 */
    private String question;

    /** 题目类型（技术基础/项目经验/追问/深入追问/挑战题等） */
    private String questionType;

    /** 期望关键词 */
    private List<String> expectedKeywords;

    /** 难度 */
    private String difficulty;

    /** 生成原因 */
    private String reason;

    /** 题目序号（主问题从 1 开始，追问继承主问题序号） */
    private int questionIndex;

    /** 是否为追问 */
    private boolean isFollowUp;

    // ========== 标准答案与建议（评估时生成） ==========
    /** 标准答案（仅主问题有） */
    private String standardAnswer;

    /** 给面试者的建议（仅主问题有） */
    private String suggestions;

    // ========== 评估结果（评估后填充） ==========
    /** 回答内容 */
    private String answer;

    /** 综合得分 */
    private Integer overallScore;

    /** 内容维度得分 */
    private Integer accuracy;
    private Integer logic;
    private Integer fluency;
    private Integer confidence;

    /** 多模态维度得分 */
    private Integer emotionScore;
    private Integer bodyLanguageScore;
    private Integer voiceToneScore;

    /** 优点列表 */
    @Builder.Default
    private List<String> strengths = new ArrayList<>();

    /** 不足列表 */
    @Builder.Default
    private List<String> weaknesses = new ArrayList<>();

    /** 详细评价 */
    private String detailedEvaluation;

    /** 是否存在多模态异常 */
    private Boolean modalityConcern;
}
