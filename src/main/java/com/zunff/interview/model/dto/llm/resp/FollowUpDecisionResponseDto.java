package com.zunff.interview.model.dto.llm.resp;

public record FollowUpDecisionResponseDto(
        Integer decisionCode,
        String decision,
        String followUpQuestion,
        String reason,
        String followUpType,
        Integer followUpTypeCode
) {
}

