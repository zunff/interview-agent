package com.zunff.interview.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "开始面试请求")
public class StartInterviewRequest {

    @Schema(description = "候选人简历内容（文本形式）", example = "张三，5年Java开发经验，熟悉Spring Boot、MySQL...")
    @NotBlank(message = "简历内容不能为空")
    private String resume;

    @Schema(description = "应聘岗位信息", example = "高级Java开发工程师，负责后端服务开发，要求熟悉分布式系统")
    @NotBlank(message = "岗位信息不能为空")
    private String jobInfo;

    @Schema(description = "最大问题数量", example = "10", defaultValue = "10")
    @Builder.Default
    private int maxQuestions = 10;

    @Schema(description = "每题最大追问次数", example = "2", defaultValue = "2")
    @Builder.Default
    private int maxFollowUps = 2;
}
