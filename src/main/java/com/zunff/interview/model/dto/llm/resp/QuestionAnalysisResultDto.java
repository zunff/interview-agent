package com.zunff.interview.model.dto.llm.resp;

/**
 * 问题分析结果
 */
public record QuestionAnalysisResultDto(
        String interviewIntent,
        String standardAnswer,
        String suggestions
) {
}