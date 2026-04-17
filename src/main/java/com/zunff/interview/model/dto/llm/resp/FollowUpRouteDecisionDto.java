package com.zunff.interview.model.dto.llm.resp;

/**
 * 追问路由决策响应DTO（只返回路由，不生成问题）
 */
public record FollowUpRouteDecisionDto(
        String decision,  // followUp / deepDive / challengeMode / nextQuestion
        String reason
) {
}
