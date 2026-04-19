package com.zunff.interview.model.dto.llm.vars;

import cn.hutool.core.bean.BeanUtil;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * question-generator-user 模板变量
 */
@Data
@Builder
public class QuestionGeneratorUserPromptVars {
    private String candidateProfile;
    private String jobInfo;
    private String roundDisplayName;
    private int count;
    private String referenceContext;
    private String responseLanguage;
    private String positionLevel;
    private String candidateLevel;
    private String difficultyRangeMin;
    private String difficultyRangeMax;
    private String difficultyPreference;

    public Map<String, Object> asMap() {
        return BeanUtil.beanToMap(this);
    }
}