package com.zunff.interview.model.dto.llm.resp;

public record JobAnalysisResponseDto(
        Integer jobTypeCode,
        Integer technicalBasicCount,
        Integer projectCount,
        Integer businessCount,
        Integer softSkillCount,
        Integer totalQuestions,
        String keyRequirements,
        String techStackSummary,
        String businessDomain,
        String softSkillsRequired,
        String company,
        String jobPosition
) {
}

