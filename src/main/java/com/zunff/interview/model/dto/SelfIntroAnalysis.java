package com.zunff.interview.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 自我介绍分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelfIntroAnalysis {

    /**
     * 表达清晰度：清晰/一般/混乱
     */
    private String clarity;

    /**
     * 关键点是否与简历匹配
     */
    private boolean keyPointsMatch;

    /**
     * 候选人强调的亮点
     */
    @Builder.Default
    private List<String> highlights = new ArrayList<>();

    /**
     * 需要关注的点
     */
    @Builder.Default
    private List<String> concerns = new ArrayList<>();

    /**
     * 初步印象评分 (1-10)
     */
    private int impressionScore;

    /**
     * 一句话总结
     */
    private String summary;
}
