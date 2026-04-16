package com.zunff.interview.model.dto.llm.resp;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public record ProjectInfoDto (
        String name,
        String role,
        List<String> highlights
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}

