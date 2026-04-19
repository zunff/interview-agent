package com.zunff.interview.model.bo;

import lombok.*;

import java.io.Serializable;
import java.util.List;

/**
 * 预生成的面试题目
 * 用于存储批量生成的面试题目及其元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 题目内容
     */
    private String question;

    /**
     * 题目类型（技术基础/项目经验/业务场景/软技能等）
     */
    private String questionType;

    /**
     * 期望关键词
     */
    private List<String> expectedKeywords;

    /**
     * 难度（easy/medium/hard）
     */
    private String difficulty;

    /**
     * 生成原因/意图说明
     */
    private String reason;

    /**
     * 题目序号（用于追踪）
     */
    private int questionIndex;
}