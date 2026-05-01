package com.zunff.interview.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillInfo {
    private String name;
    private String category;
    private String level;
    private int yearsOfExperience;
}
