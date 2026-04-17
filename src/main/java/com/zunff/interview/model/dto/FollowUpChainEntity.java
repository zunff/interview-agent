package com.zunff.interview.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 追问链路条目
 * 记录每次追问的问题和评估详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpChainEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 追问问题内容 */
    private String followUpQuestion;

    /** 该追问回答的详细评价 */
    private String detailedEvaluation;

    /** 该追问回答的综合得分 */
    private Integer overallScore;
}