package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.dto.analysis.FrameWithTimestamp;
import com.zunff.interview.model.dto.analysis.TranscriptEntry;
import com.zunff.interview.service.extend.MultimodalAnalysisService;
import com.zunff.interview.agent.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Omni 多模态综合评估节点
 * 一次性调用 Qwen-Omni 模型综合分析转录文本+时间戳、视频帧+时间戳、原始音频WAV
 * 替代原有的 VisionAnalysisNode + AudioAnalysisNode + AggregateAnalysisNode 三节点流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComprehensiveEvaluationNode {

    private final MultimodalAnalysisService multimodalAnalysisService;

    /**
     * 执行 Omni 多模态综合评估
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始综合评估");

        String question = state.currentQuestion();
        String questionType = state.questionType();
        String answerText = state.answerText();
        List<TranscriptEntry> transcriptEntries = state.transcriptEntries();
        List<FrameWithTimestamp> framesWithTimestamps = state.answerFramesWithTimestamps();
        String answerAudio = (String) state.data().get(InterviewState.ANSWER_AUDIO);

        log.info("评估数据：问题类型={}, 转录文本长度={}, 转录条目数={}, 视频帧数={}, 音频={}",
                questionType,
                answerText != null ? answerText.length() : 0,
                transcriptEntries.size(),
                framesWithTimestamps.size(),
                answerAudio != null && !answerAudio.isEmpty() ? "有" : "无");

        // 根据问题类型选择评估模板
        QuestionType type = QuestionType.fromDisplayName(questionType);
        String evaluationPrompt = type.getEvaluationPrompt();
        log.debug("选择评估模板: {} -> {}", questionType, evaluationPrompt);

        try {
            EvaluationBO evaluation = multimodalAnalysisService.comprehensiveOmniEvaluation(
                    question,
                    answerText,
                    transcriptEntries,
                    framesWithTimestamps,
                    answerAudio,
                    evaluationPrompt
            );

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.CURRENT_EVALUATION, evaluation);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("综合评估完成，综合得分: {}, 问题类型: {}", evaluation.getOverallScore(), questionType);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("综合评估失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            // 返回默认评估，继续流转
            EvaluationBO defaultEval = EvaluationBO.builder()
                    .accuracy(60).logic(60).fluency(60).confidence(60)
                    .emotionScore(70).bodyLanguageScore(70).voiceToneScore(70)
                    .overallScore(60)
                    .detailedEvaluation("评估失败，使用默认评分")
                    .build();
            updates.put(InterviewState.CURRENT_EVALUATION, defaultEval);
            return CompletableFuture.completedFuture(updates);
        }
    }
}
