package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.dto.GeneratedQuestion;
import com.zunff.interview.service.extend.MultimodalAnalysisService;
import com.zunff.interview.agent.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 追问决策节点
 * 根据综合评估结果（EvaluationBO，已包含多模态分数字段）进行追问决策
 *
 * 架构调整：
 * - 只返回路由决策（followUp/deepDive/challengeMode/nextQuestion）
 * - 不再生成具体问题，由后续节点负责
 * - 采用两阶段决策：快速预判（代码） + LLM 精细决策
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpDecisionNode {

    private final MultimodalAnalysisService multimodalAnalysisService;

    private static final String NEXT_QUESTION = "nextQuestion";

    /**
     * 执行追问决策（两阶段决策）
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始追问决策，当前追问次数: {}/{}",
                state.followUpCount(), state.maxFollowUpsForCurrentRound());

        EvaluationBO evaluation = state.getCurrentEvaluation();
        GeneratedQuestion generatedQuestion = state.getCurrentGeneratedQuestion();
        int followUpCount = state.followUpCount();
        int maxFollowUps = state.maxFollowUpsForCurrentRound();

        // ========== 阶段一：快速预判（基于规则） ==========
        if (shouldSkipFollowUpByCode(evaluation, generatedQuestion, state)) {
            String quickDecision = quickDecisionByCode(evaluation, generatedQuestion, state);
            log.info("快速预判命中，决策: {}", quickDecision);

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.DECISION, quickDecision);
            CircuitBreakerHelper.recordSuccess(updates);
            return CompletableFuture.completedFuture(updates);
        }

        // ========== 阶段二：LLM 精细决策 ==========
        try {
            String decision = multimodalAnalysisService.decideFollowUpRoute(
                    evaluation,
                    generatedQuestion,
                    followUpCount,
                    maxFollowUps
            );

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.DECISION, decision);
            CircuitBreakerHelper.recordSuccess(updates);
            log.info("LLM 精细决策: {}", decision);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("LLM 决策失败，降级为 nextQuestion", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            updates.put(InterviewState.DECISION, NEXT_QUESTION);
            return CompletableFuture.completedFuture(updates);
        }
    }

    /**
     * 快速预判：基于规则快速决定是否跳过 LLM 决策
     * @return true 表示可以直接跳过追问（返回 nextQuestion），false 表示需要 LLM 精细决策
     */
    private boolean shouldSkipFollowUpByCode(EvaluationBO eval, GeneratedQuestion question, InterviewState state) {
        int followUpCount = state.followUpCount();
        int maxFollowUps = state.maxFollowUpsForCurrentRound();

        // 已达上限，跳过
        if (followUpCount >= maxFollowUps) return true;

        // 高分 + 无异常 + 已有追问，跳过
        if (eval.getOverallScore() >= 85
                && !eval.isModalityConcern()
                && eval.getWeaknesses().isEmpty()
                && followUpCount >= 1) {
            return true;
        }

        // Hard 难度 + 低分，**必须追问**（不跳过 LLM）
        if (question != null && "hard".equals(question.getDifficulty()) && eval.getOverallScore() < 60) {
            return false;
        }

        // Easy 难度 + 高分，跳过
        if (question != null && "easy".equals(question.getDifficulty()) && eval.getOverallScore() >= 70) {
            return true;
        }

        // 其他情况需要 LLM 精细决策
        return false;
    }

    /**
     * 基于规则的快速路由决策（不调用 LLM）
     */
    private String quickDecisionByCode(EvaluationBO eval, GeneratedQuestion question, InterviewState state) {
        int followUpCount = state.followUpCount();
        int maxFollowUps = state.maxFollowUpsForCurrentRound();

        if (followUpCount >= maxFollowUps) return NEXT_QUESTION;

        // Hard 题目低分优先 deepDive
        if (question != null && "hard".equals(question.getDifficulty()) && eval.getOverallScore() < 50) {
            return "deepDive";
        }

        // 高分优先 challengeMode
        if (eval.getOverallScore() > 90 && eval.getWeaknesses().isEmpty()) {
            return "challengeMode";
        }

        // 中等分数 + 有弱点或多模态异常
        if (eval.getOverallScore() < 70 || !eval.getWeaknesses().isEmpty() || eval.isModalityConcern()) {
            return "followUp";
        }

        return NEXT_QUESTION;
    }
}
