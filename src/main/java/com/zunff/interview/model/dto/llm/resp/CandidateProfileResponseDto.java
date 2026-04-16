package com.zunff.interview.model.dto.llm.resp;

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
        String summary
) {
}

