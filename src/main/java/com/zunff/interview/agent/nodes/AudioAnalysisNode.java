package com.zunff.interview.agent.nodes;

import cn.hutool.core.util.StrUtil;
import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.model.dto.analysis.AudioAnalysisResult;
import com.zunff.interview.service.extend.MultimodalAnalysisService;
import com.zunff.interview.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 音频分析节点
 * 分析候选人的音频（情感分析）
 * ASR转录已在WebSocket层实时完成，此处只做情感分析
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioAnalysisNode {

    private final MultimodalAnalysisService multimodalAnalysisService;

    /**
     * 执行音频分析
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始音频分析（情感分析）");

        String existingAnswerText = state.answerText();
        log.info("音频分析节点：文本存在={}, 文本长度={}",
                StrUtil.isNotEmpty(existingAnswerText),
                existingAnswerText != null ? existingAnswerText.length() : 0);

        try {
            AudioAnalysisResult audioResult;
            if (existingAnswerText != null && !existingAnswerText.isEmpty()) {
                // 从已有转录文本做情感分析
                audioResult = multimodalAnalysisService.analyzeAudioFromTranscript(existingAnswerText);
            } else {
                log.warn("无转录文本，使用默认音频分析结果");
                audioResult = AudioAnalysisResult.defaultResult();
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.AUDIO_ANALYSIS_RESULT, audioResult);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("音频分析完成，语音语调得分: {}", audioResult.getVoiceToneScore());

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("音频分析失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            // 返回默认结果，继续流转
            updates.put(InterviewState.AUDIO_ANALYSIS_RESULT, AudioAnalysisResult.defaultResult());
            return CompletableFuture.completedFuture(updates);
        }
    }
}
