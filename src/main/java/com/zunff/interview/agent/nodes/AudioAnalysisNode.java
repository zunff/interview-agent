package com.zunff.interview.agent.nodes;

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
 * 分析候选人的音频（语音转文字 + 情感分析）
 * 作为并行分析分支的一部分
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
        log.info("开始音频分析");

        String answerAudio = (String) state.data().get(InterviewState.ANSWER_AUDIO);
        String existingAnswerText = state.answerText();
        log.info("音频分析节点：音频数据存在={}, 文本存在={}",
                answerAudio != null && !answerAudio.isEmpty(),
                existingAnswerText != null && !existingAnswerText.isEmpty());

        try {
            AudioAnalysisResult audioResult = multimodalAnalysisService.analyzeAudio(answerAudio);

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.AUDIO_ANALYSIS_RESULT, audioResult);
            CircuitBreakerHelper.recordSuccess(updates);

            // 如果前端没传文本，用服务端 STT 转写结果
            if ((existingAnswerText == null || existingAnswerText.isEmpty())
                    && audioResult.getTranscribedText() != null
                    && !audioResult.getTranscribedText().isEmpty()) {
                updates.put(InterviewState.ANSWER_TEXT, audioResult.getTranscribedText());
                log.info("使用服务端 STT 转写结果作为回答文本，长度: {}", audioResult.getTranscribedText().length());
            }

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
