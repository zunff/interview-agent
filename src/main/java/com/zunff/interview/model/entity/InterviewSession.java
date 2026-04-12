package com.zunff.interview.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 面试会话实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("interview_session")
public class InterviewSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话ID (唯一标识) */
    private String sessionId;

    /** 简历内容 */
    private String resume;

    /** 岗位信息 */
    private String jobInfo;

    /** 技术轮最大问题数 */
    private Integer maxTechnicalQuestions;

    /** 业务轮最大问题数 */
    private Integer maxBusinessQuestions;

    /** 每题最大追问数 */
    private Integer maxFollowUps;

    /** 当前问题索引 */
    private Integer currentQuestionIndex;

    /** 会话状态 */
    private String status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 最终报告 */
    private String report;

    /**
     * 会话状态枚举
     */
    public enum Status {
        WAITING,        // 等待开始
        IN_PROGRESS,    // 进行中
        WAITING_ANSWER, // 等待回答
        DISCONNECTED,   // 连接断开
        FINISHED        // 已结束
    }
}
