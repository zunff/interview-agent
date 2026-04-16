package com.zunff.interview.model.dto.llm.resp;

import java.util.List;

public record EvaluationResponseDto(
        Integer accuracy,
        Integer logic,
        Integer fluency,
        Integer confidence,
        Integer emotionScore,
        Integer bodyLanguageScore,
        Integer voiceToneScore,
        Integer overallScore,
        List<String> strengths,
        List<String> weaknesses,
        String detailedEvaluation
) {
}

