package com.zunff.interview.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开始面试请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartInterviewRequest {

    /** 简历内容（文本形式） */
    @NotBlank(message = "简历内容不能为空")
    private String resume;

    /** 岗位信息 */
    @NotBlank(message = "岗位信息不能为空")
    private String jobInfo;

    /** 面试类型：一面/二面 */
    @Builder.Default
    private String interviewType = "一面";

    /** 最大问题数 */
    @Builder.Default
    private int maxQuestions = 10;

    /** 每题最大追问次数 */
    @Builder.Default
    private int maxFollowUps = 2;
}
