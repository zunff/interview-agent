package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.bo.InterviewQuestionBO;
import com.zunff.interview.model.bo.FollowUpChainEntity;
import com.zunff.interview.model.bo.GeneratedQuestion;
import com.zunff.interview.model.bo.analysis.FrameWithTimestamp;
import com.zunff.interview.model.bo.analysis.TranscriptEntry;
import com.zunff.interview.service.extend.MultimodalAnalysisService;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.utils.OmniOverallScoreUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        GeneratedQuestion generatedQuestion = state.getCurrentGeneratedQuestion(); // 获取题目元信息

        log.info("评估数据：问题类型={}, 转录文本长度={}, 转录条目数={}, 视频帧数={}, 音频={}, 难度={}",
                questionType,
                answerText != null ? answerText.length() : 0,
                transcriptEntries.size(),
                framesWithTimestamps.size(),
                answerAudio != null && !answerAudio.isEmpty() ? "有" : "无",
                generatedQuestion.getDifficulty());

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
                    evaluationPrompt,
                    generatedQuestion  // 传递题目元信息
            );

            Map<String, Object> updates = new HashMap<>();

            // 构建 InterviewQuestionBO（标准答案和建议已在评估时生成）
            InterviewQuestionBO questionBO = InterviewQuestionBO.builder()
                    .question(question)
                    .questionType(questionType)
                    .expectedKeywords(generatedQuestion.getExpectedKeywords())
                    .difficulty(generatedQuestion.getDifficulty())
                    .reason(generatedQuestion.getReason())
                    .questionIndex(state.questionIndex())
                    .isFollowUp(type.isFollowUpType())
                    .standardAnswer(evaluation.getStandardAnswer())
                    .suggestions(evaluation.getSuggestions())
                    .answer(answerText)
                    .overallScore(evaluation.getOverallScore())
                    .accuracy(evaluation.getAccuracy())
                    .logic(evaluation.getLogic())
                    .fluency(evaluation.getFluency())
                    .confidence(evaluation.getConfidence())
                    .emotionScore(evaluation.getEmotionScore())
                    .bodyLanguageScore(evaluation.getBodyLanguageScore())
                    .voiceToneScore(evaluation.getVoiceToneScore())
                    .strengths(evaluation.getStrengths())
                    .weaknesses(evaluation.getWeaknesses())
                    .detailedEvaluation(evaluation.getDetailedEvaluation())
                    .modalityConcern(evaluation.isModalityConcern())
                    .build();

            // 追加到已评估列表
            String listKey = state.isTechnicalRound()
                    ? InterviewState.EVALUATED_TECHNICAL_QUESTIONS
                    : InterviewState.EVALUATED_BUSINESS_QUESTIONS;
            updates.put(listKey, questionBO);

            if (type.isFollowUpType()) {
                FollowUpChainEntity entry = FollowUpChainEntity.builder()
                        .followUpQuestion(question)
                        .detailedEvaluation(evaluation.getDetailedEvaluation())
                        .overallScore(evaluation.getOverallScore())
                        .build();
                List<FollowUpChainEntity> updatedChain = new ArrayList<>(state.followUpChain());
                updatedChain.add(entry);
                updates.put(InterviewState.FOLLOW_UP_CHAIN, updatedChain);
                log.info("记录追问链路: 问题={}, 得分={}", question, evaluation.getOverallScore());
            }

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
                    .overallScore(OmniOverallScoreUtils.computeOverallScore(60, 60, 60, 60, 70, 70, 70))
                    .detailedEvaluation("评估失败，使用默认评分")
                    .generatedQuestion(generatedQuestion)  // 即使失败也关联元信息
                    .build();
            updates.put(InterviewState.CURRENT_EVALUATION, defaultEval);
            return CompletableFuture.completedFuture(updates);
        }
    }
}
