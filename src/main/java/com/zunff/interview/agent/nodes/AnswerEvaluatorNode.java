package com.zunff.interview.agent.nodes;

import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.dto.analysis.AudioAnalysisResult;
import com.zunff.interview.model.dto.analysis.VideoAnalysisResult;
import com.zunff.interview.service.MultimodalAnalysisService;
import com.zunff.interview.service.PromptTemplateService;
import com.zunff.interview.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 答案评估节点
 * 根据问题类型选择不同的评估标准，综合评估候选人的回答
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnswerEvaluatorNode {

    private final MultimodalAnalysisService multimodalAnalysisService;
    private final PromptTemplateService promptTemplateService;

    /**
     * 执行答案评估
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始评估答案，问题序号: {}, 问题类型: {}", state.questionIndex(), state.questionType());

        String question = state.currentQuestion();
        String answerText = state.answerText();
        String questionType = state.questionType();
        String answerAudio = (String) state.data().get(InterviewState.ANSWER_AUDIO);

        @SuppressWarnings("unchecked")
        List<String> answerFrames = (List<String>) state.data().getOrDefault(
                InterviewState.ANSWER_FRAMES,
                List.of()
        );

        try {
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

            // 3. 根据问题类型选择评估模板
            String evaluationPrompt = getEvaluationPromptByQuestionType(questionType);
            log.debug("选择评估模板: {} -> {}", questionType, evaluationPrompt);

            // 4. 综合评估
            log.debug("开始综合评估");
            EvaluationBO evaluation = multimodalAnalysisService.comprehensiveEvaluate(
                    question,
                    answerText,
                    videoResult,
                    audioResult,
                    evaluationPrompt
            );

            // 构建状态更新
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.CURRENT_EVALUATION, evaluation);
            updates.put(InterviewState.NEED_FOLLOW_UP, evaluation.isNeedFollowUp());
            updates.put(InterviewState.FOLLOW_UP_QUESTION, evaluation.getFollowUpSuggestion());
            // LLM 调用成功，重置失败计数
            updates.put(InterviewState.CONSECUTIVE_LLM_FAILURES, 0);

            // 传递多模态追问建议到状态
            if (evaluation.getModalityFollowUpSuggestion() != null && !evaluation.getModalityFollowUpSuggestion().isEmpty()) {
                updates.put(InterviewState.MODALITY_FOLLOW_UP_SUGGESTION, evaluation.getModalityFollowUpSuggestion());
                updates.put(InterviewState.MODALITY_CONCERN, evaluation.isModalityConcern());
                log.debug("多模态建议: {}", evaluation.getModalityFollowUpSuggestion());
            }

            log.info("评估完成，综合得分: {}, 是否需要追问: {}, 问题类型: {}",
                    evaluation.getOverallScore(), evaluation.isNeedFollowUp(), questionType);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("评估答案失败", e);
            int failures = state.consecutiveLLMFailures() + 1;
            if (failures >= state.maxLLMFailures()) {
                throw new RuntimeException("LLM 连续调用失败达到 " + state.maxLLMFailures() + " 次，触发熔断", e);
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.CONSECUTIVE_LLM_FAILURES, failures);
            // 返回默认评估，继续流转
            EvaluationBO defaultEval = EvaluationBO.builder()
                    .accuracy(60).logic(60).fluency(60).confidence(60)
                    .overallScore(60).needFollowUp(false)
                    .detailedEvaluation("评估失败，使用默认评分")
                    .build();
            updates.put(InterviewState.CURRENT_EVALUATION, defaultEval);
            updates.put(InterviewState.NEED_FOLLOW_UP, false);
            return CompletableFuture.completedFuture(updates);
        }
    }

    /**
     * 根据问题类型选择评估 Prompt 模板
     */
    private String getEvaluationPromptByQuestionType(String questionType) {
        if (questionType == null || questionType.isEmpty()) {
            return "evaluation";
        }

        // 技术基础类问题
        if (isTechnicalBasicQuestion(questionType)) {
            return "evaluation-technical";
        }

        // 项目经验类问题
        if (isProjectQuestion(questionType)) {
            return "evaluation-project";
        }

        // 业务场景类问题
        if (isBusinessQuestion(questionType)) {
            return "evaluation-business";
        }

        // 软技能类问题
        if (isSoftSkillQuestion(questionType)) {
            return "evaluation-soft";
        }

        // 默认使用通用评估模板
        return "evaluation";
    }

    /**
     * 判断是否为技术基础问题
     */
    private boolean isTechnicalBasicQuestion(String questionType) {
        return questionType.contains("技术基础") ||
               questionType.contains("技术难点") ||
               questionType.equals(QuestionType.TECHNICAL_BASIC.getDisplayName());
    }

    /**
     * 判断是否为项目经验问题
     */
    private boolean isProjectQuestion(String questionType) {
        return questionType.contains("项目") ||
               questionType.contains("项目经验") ||
               questionType.equals(QuestionType.PROJECT_EXPERIENCE.getDisplayName());
    }

    /**
     * 判断是否为业务场景问题
     */
    private boolean isBusinessQuestion(String questionType) {
        return questionType.contains("业务") ||
               questionType.contains("场景") ||
               questionType.contains("业务理解") ||
               questionType.contains("场景分析");
    }

    /**
     * 判断是否为软技能问题
     */
    private boolean isSoftSkillQuestion(String questionType) {
        return questionType.contains("软技能") ||
               questionType.contains("沟通") ||
               questionType.contains("协作") ||
               questionType.contains("职业素养");
    }
}
