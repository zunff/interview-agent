package com.zunff.interview.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 面试知识库实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("interview_knowledge")
public class InterviewKnowledgeEntity {

    @TableId(type = IdType.AUTO)
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
     * 面试类型：技术面/业务面/HR面试
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
     * 来源
     */
    private String source;

    /**
     * 标签
     */
    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String[] tags;
}
