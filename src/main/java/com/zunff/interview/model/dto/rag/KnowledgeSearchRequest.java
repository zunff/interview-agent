package com.zunff.interview.model.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库检索请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSearchRequest {

    /**
     * 查询文本
     */
    private String query;

    /**
     * 面试类型过滤
     */
    private String questionType;

    /**
     * 公司过滤
     */
    private String company;

    /**
     * 岗位过滤
     */
    private String jobPosition;

    /**
     * 返回数量
     */
    private int topK;

    /**
     * 最小相似度阈值
     */
    private double similarityThreshold;
}
