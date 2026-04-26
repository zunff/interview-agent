package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.constant.Difficulty;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.bo.GeneratedQuestion;
import com.zunff.interview.service.extend.MultimodalAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

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
        RouteDecision quickDecision = tryQuickDecision(evaluation, generatedQuestion, state);
        if (quickDecision != null) {
            log.info("快速预判命中，决策: {}", quickDecision.getValue());

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.DECISION, quickDecision.getValue());
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
            updates.put(InterviewState.DECISION, RouteDecision.NEXT_QUESTION.getValue());
            return CompletableFuture.completedFuture(updates);
        }
    }

    /**
     * 尝试快速决策（基于规则）
     * @return 返回决策结果，如果返回 null 表示需要 LLM 精细决策
     */
    private RouteDecision tryQuickDecision(EvaluationBO eval, GeneratedQuestion question, InterviewState state) {
        int rawScore = eval.getOverallScore();
        int used = state.followUpCount();
        int max = state.maxFollowUpsForCurrentRound();
        int remaining = max - used;

        Difficulty difficulty = question != null && question.getDifficulty() != null
                ? Difficulty.fromCode(question.getDifficulty())
                : Difficulty.MEDIUM;
        boolean hasWeakness = !CollectionUtils.isEmpty(eval.getWeaknesses());
        boolean concern = eval.isModalityConcern();

        // 难度校准：hard 题降低门槛，easy 题提高要求
        int difficultyOffset = switch (difficulty) {
            case HARD -> 15;
            case MEDIUM -> 0;
            case EASY -> -10;
        };
        int score = Math.max(0, Math.min(100, rawScore + difficultyOffset));

        // 1. 已达追问上限
        if (used >= max) {
            return RouteDecision.NEXT_QUESTION;
        }

        // 2. 低分且有弱点 → 深度挖掘
        if (score < 50 && hasWeakness && remaining >= 2) {
            return RouteDecision.DEEP_DIVE;
        }

        // 3. 极高分、无弱点、有预算 → 挑战模式
        int challengeThreshold = difficulty == Difficulty.EASY ? 85 : 90;
        if (score > challengeThreshold && !hasWeakness && used == 0 && remaining >= 2) {
            return RouteDecision.CHALLENGE_MODE;
        }

        // 4. 表现好的标准题（无弱点）→ 下一题
        if (score >= 75 && !hasWeakness) {
            return RouteDecision.NEXT_QUESTION;
        }

        // 5. 中等偏上分数（70-84）且只有内容弱点、无模态异常 → 交给 LLM 精细决策
        //    避免所有 70-80 分的评估都走 FOLLOW_UP 导致追问类型单一
        if (score >= 70 && hasWeakness && !concern) {
            return null;
        }

        // 6. 仅剩最后一次追问额度
        if (remaining <= 1) {
            if (concern) {
                return RouteDecision.FOLLOW_UP;
            }
            return (score >= 70 && !hasWeakness)
                    ? RouteDecision.NEXT_QUESTION
                    : RouteDecision.FOLLOW_UP;
        }

        // 7. 有余量且有弱点或模态异常 → 追问
        if (hasWeakness || concern) {
            return RouteDecision.FOLLOW_UP;
        }

        // 8. 其余交给 LLM 精细决策
        return null;
    }
}
