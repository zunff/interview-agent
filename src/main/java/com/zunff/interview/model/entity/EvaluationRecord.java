package com.zunff.interview.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zunff.interview.config.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评估记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "evaluation_record", autoResultMap = true)
public class EvaluationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的会话ID */
    private String sessionId;

    /** 问题索引 */
    private Integer questionIndex;

    /** 问题内容 */
    private String question;

    /** 回答内容 */
    private String answer;

    /** 准确性评分 */
    private Integer accuracy;

    /** 逻辑性评分 */
    private Integer logic;

    /** 流畅度评分 */
    private Integer fluency;

    /** 自信度评分 */
    private Integer confidence;

    /** 情感评分 */
    private Integer emotionScore;

    /** 肢体语言评分 */
    private Integer bodyLanguageScore;

    /** 语调评分 */
    private Integer voiceToneScore;

    /** 综合评分 */
    private Integer overallScore;

    /** 优点 (JSON) */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> strengths;

    /** 不足 (JSON) */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> weaknesses;

    /** 详细评估 */
    private String detailedEvaluation;

    /** 题目类型展示名（与 GeneratedQuestion.questionType 一致） */
    private String questionType;

    /** 题目难度 easy/medium/hard */
    private String difficulty;

    /** 期望关键词（JSON 数组） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private List<String> expectedKeywords;

    /** 是否存在多模态异常 */
    private Boolean modalityConcern;

    /** 标准答案（仅主问题有） */
    private String standardAnswer;

    /** 给面试者的建议（仅主问题有） */
    private String suggestions;

    /** 是否为追问 */
    private Boolean isFollowUp;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
