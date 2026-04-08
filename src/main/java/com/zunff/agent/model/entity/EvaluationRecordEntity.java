package com.zunff.agent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评估记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("evaluation_record")
public class EvaluationRecordEntity {

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
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object strengths;

    /** 不足 (JSON) */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private Object weaknesses;

    /** 详细评估 */
    private String detailedEvaluation;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
