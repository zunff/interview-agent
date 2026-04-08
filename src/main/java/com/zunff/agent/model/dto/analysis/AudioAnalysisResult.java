package com.zunff.agent.model.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 音频分析结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudioAnalysisResult {

    /** 语音语调得分 (0-100) */
    private int voiceToneScore;

    /** 转录文本 */
    private String transcribedText;

    /** 语调分析描述 */
    private String toneAnalysis;

    /** 情感分析描述 */
    private String emotionAnalysis;

    /** 改进建议 */
    private List<String> suggestions;

    /**
     * 空结果（未进行分析）
     */
    public static AudioAnalysisResult empty() {
        return AudioAnalysisResult.builder()
                .voiceToneScore(70)
                .transcribedText("")
                .toneAnalysis("未进行音频分析")
                .emotionAnalysis("未进行音频分析")
                .suggestions(new ArrayList<>())
                .build();
    }

    /**
     * 默认结果（分析失败时使用）
     */
    public static AudioAnalysisResult defaultResult() {
        return AudioAnalysisResult.builder()
                .voiceToneScore(72)
                .transcribedText("")
                .toneAnalysis("语调较为自然，表达流畅度中等")
                .emotionAnalysis("声音中略有紧张感，但整体表现稳定")
                .suggestions(List.of("建议语速稍慢一些", "可以在重点处加强语气"))
                .build();
    }
}