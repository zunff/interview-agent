package com.zunff.interview.model.dto.llm.resp;

import java.util.List;

public record ProjectInfoDto(
        String name,
        String role,
        List<String> highlights
) {
}

