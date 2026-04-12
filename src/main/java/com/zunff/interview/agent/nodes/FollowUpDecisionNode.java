package com.zunff.interview.agent.nodes;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.bo.FollowUpDecisionBO;
import com.zunff.interview.model.dto.analysis.AudioAnalysisResult;
import com.zunff.interview.model.dto.analysis.VisionAnalysisResult;
import com.zunff.interview.service.extend.MultimodalAnalysisService;
import com.zunff.interview.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 追问决策节点
 * 根据评估结果和多模态分析，直接输出路由决策
 *
 * 使用独立的 followup-decision.prompt 进行追问决策
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpDecisionNode {

    private final MultimodalAnalysisService multimodalAnalysisService;

    private static final String NEXT_QUESTION = "nextQuestion";

    /**
     * 执行追问决策
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始追问决策，当前追问次数: {}/{}",
                state.followUpCount(), state.maxFollowUpsForCurrentRound());

        String question = state.currentQuestion();
        EvaluationBO evaluation = state.getCurrentEvaluation();
        VisionAnalysisResult visionResult = state.visionAnalysisResult();
        AudioAnalysisResult audioResult = state.audioAnalysisResult();
        String answerText = audioResult.getTranscribedText();
        int followUpCount = state.followUpCount();
        int maxFollowUps = state.maxFollowUpsForCurrentRound();

        // 检查是否已达到最大追问次数
        if (followUpCount >= maxFollowUps) {
            log.info("已达到当前轮次最大追问次数 {}，进入下一题", maxFollowUps);
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.DECISION, NEXT_QUESTION);
            return CompletableFuture.completedFuture(updates);
        }

        try {
            // 调用独立的追问决策方法
            FollowUpDecisionBO decision = multimodalAnalysisService.decideFollowUp(
                    question,
                    answerText,
                    evaluation,
                    visionResult,
                    audioResult,
                    followUpCount,
                    maxFollowUps
            );

            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.recordSuccess(updates);

            // 输出 LLM 的完整决策
            String decisionValue = decision.getDecision();
            updates.put(InterviewState.DECISION, decisionValue);

            // 如果是追问类决策，设置追问问题
            if (!NEXT_QUESTION.equals(decisionValue) && decision.getFollowUpQuestion() != null) {
                updates.put(InterviewState.FOLLOW_UP_QUESTION, decision.getFollowUpQuestion());
                updates.put(InterviewState.FOLLOW_UP_COUNT, followUpCount + 1);
                log.info("路由决策: {}, 追问: {}, 原因: {}, 类型: {}",
                        decisionValue, decision.getFollowUpQuestion(), decision.getReason(), decision.getFollowUpType());
            } else {
                log.info("路由决策: {}, 原因: {}", decisionValue, decision.getReason());
            }

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("追问决策失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            updates.put(InterviewState.DECISION, NEXT_QUESTION);
            return CompletableFuture.completedFuture(updates);
        }
    }
}
