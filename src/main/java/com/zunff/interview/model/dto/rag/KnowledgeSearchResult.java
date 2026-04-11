package com.zunff.interview.model.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库检索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchResult {

    /**
     * 知识库ID
     */
    private Long id;

    /**
     * 问题内容
     */
    private String question;

    /**
     * 参考答案
     */
    private String answer;

    /**
     * 面试类型
     */
    private String questionType;

    /**
     * 关联公司
     */
    private String company;

    /**
     * 关联岗位
     */
    private String jobPosition;

    /**
     * 分类
     */
    private String category;

    /**
     * 难度
     */
    private String difficulty;

    /**
     * 相似度得分
     */
    private double similarityScore;
}
