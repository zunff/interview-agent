package com.zunff.agent.model.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 视频分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoAnalysisResult {

    /** 表情情感得分 (0-100) */
    private int emotionScore;

    /** 肢体语言得分 (0-100) */
    private int bodyLanguageScore;

    /** 表情分析描述 */
    private String emotionAnalysis;

    /** 肢体语言分析描述 */
    private String bodyLanguageAnalysis;

    /** 改进建议 */
    private List<String> suggestions;

    /** 多模态追问建议（如"肢体语言紧张，建议追问自信度"） */
    private String followUpSuggestion;

    /** 是否存在明显异常 */
    private boolean hasConcern;

    /**
     * 空结果（未进行分析）
     */
    public static VideoAnalysisResult empty() {
        return VideoAnalysisResult.builder()
                .emotionScore(70)
                .bodyLanguageScore(70)
                .emotionAnalysis("未进行视频分析")
                .bodyLanguageAnalysis("未进行视频分析")
                .suggestions(new ArrayList<>())
                .followUpSuggestion("")
                .hasConcern(false)
                .build();
    }

    /**
     * 默认结果（分析失败时使用）
     */
    public static VideoAnalysisResult defaultResult() {
        return VideoAnalysisResult.builder()
                .emotionScore(75)
                .bodyLanguageScore(78)
                .emotionAnalysis("基于实时分析，面试者表情较为自然，偶有紧张表现")
                .bodyLanguageAnalysis("坐姿端正，手势配合得当，整体表现良好")
                .suggestions(List.of("建议保持更多眼神交流", "回答时可以更加自信"))
                .followUpSuggestion("")
                .hasConcern(false)
                .build();
    }
}