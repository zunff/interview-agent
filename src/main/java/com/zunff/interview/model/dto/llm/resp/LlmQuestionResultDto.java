package com.zunff.interview.model.dto.llm.resp;

import java.util.List;

public record LlmQuestionResultDto(
        String question,
        Integer questionTypeCode,
        List<String> expectedKeywords,
        String difficulty,
        String reason,
        String interviewIntent
) {
}

