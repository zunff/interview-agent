package com.zunff.interview.model.dto.llm.resp;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record CandidateProfileResponseDto(
        List<String> techStack,
        List<ProjectInfoDto> keyProjects,
        Integer workYears,
        String education,
        String selfIntroConsistency,
        Integer selfIntroConsistencyCode,
        List<String> highlights,
        List<String> concerns,
        Integer impressionScore,
        String summary,
        // 级别匹配相关字段（由 LLM 判断）
        Integer candidateLevelCode,
        Integer positionFitScore,
        String difficultyRangeMin,
        String difficultyRangeMax,
        String difficultyPreference
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}

