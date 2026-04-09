package com.zunff.agent.agent.nodes;

import com.zunff.agent.model.bo.EvaluationBO;
import com.zunff.agent.model.dto.analysis.AudioAnalysisResult;
import com.zunff.agent.model.dto.analysis.VideoAnalysisResult;
import com.zunff.agent.service.MultimodalAnalysisService;
import com.zunff.agent.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 答案评估节点
 * 综合评估候选人的回答
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerEvaluatorNode {

    private final MultimodalAnalysisService multimodalAnalysisService;

    /**
     * 执行答案评估
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始评估答案，问题序号: {}", state.questionIndex());

        String question = state.currentQuestion();
        String answerText = state.answerText();
        String answerAudio = (String) state.data().get(InterviewState.ANSWER_AUDIO);

        @SuppressWarnings("unchecked")
        List<String> answerFrames = (List<String>) state.data().getOrDefault(
                InterviewState.ANSWER_FRAMES,
                List.of()
        );

        // 1. 分析视频帧
        log.debug("开始视频帧分析，帧数: {}", answerFrames.size());
        VideoAnalysisResult videoResult =
                multimodalAnalysisService.analyzeVideoFrames(answerFrames);

        // 2. 分析音频
        log.debug("开始音频分析");
        AudioAnalysisResult audioResult =
                answerAudio != null && !answerAudio.isEmpty()
                        ? multimodalAnalysisService.analyzeAudio(answerAudio)
                        : AudioAnalysisResult.empty();

        // 3. 综合评估
        log.debug("开始综合评估");
        EvaluationBO evaluation = multimodalAnalysisService.comprehensiveEvaluate(
                question,
                answerText,
                videoResult,
                audioResult
        );

        // 构建状态更新
        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.CURRENT_EVALUATION, evaluation);
        updates.put(InterviewState.NEED_FOLLOW_UP, evaluation.isNeedFollowUp());
        updates.put(InterviewState.FOLLOW_UP_QUESTION, evaluation.getFollowUpSuggestion());

        // 传递多模态追问建议到状态
        if (evaluation.getModalityFollowUpSuggestion() != null && !evaluation.getModalityFollowUpSuggestion().isEmpty()) {
            updates.put(InterviewState.MODALITY_FOLLOW_UP_SUGGESTION, evaluation.getModalityFollowUpSuggestion());
            updates.put(InterviewState.MODALITY_CONCERN, evaluation.isModalityConcern());
            log.debug("多模态建议: {}", evaluation.getModalityFollowUpSuggestion());
        }

        log.info("评估完成，综合得分: {}, 是否需要追问: {}",
                evaluation.getOverallScore(), evaluation.isNeedFollowUp());

        return CompletableFuture.completedFuture(updates);
    }
}
