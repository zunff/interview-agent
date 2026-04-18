package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.constant.Difficulty;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.dto.GeneratedQuestion;
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
        int score = eval.getOverallScore();
        int used = state.followUpCount();
        int max = state.maxFollowUpsForCurrentRound();
        int remaining = max - used;

        Difficulty difficulty = question != null && question.getDifficulty() != null
                ? Difficulty.fromCode(question.getDifficulty())
                : Difficulty.MEDIUM;
        boolean hasWeakness = !CollectionUtils.isEmpty(eval.getWeaknesses());
        boolean concern = eval.isModalityConcern();

        // 1. 已达追问上限
        if (used >= max) {
            return RouteDecision.NEXT_QUESTION;
        }

        // 2. Hard 题极低分：深度挖掘（需至少还剩 2 次追问额度，避免用掉最后一次）
        if (difficulty == Difficulty.HARD && score < 50 && remaining >= 2) {
            return RouteDecision.DEEP_DIVE;
        }

        // 3. 极高分、无弱点、无多模态异常、首轮、且预算足够一次有意义的挑战
        if (score > 90 && !hasWeakness && !concern && used == 0 && remaining >= 2) {
            return RouteDecision.CHALLENGE_MODE;
        }

        // 4. Easy 题表现尚可且无多模态异常：换题
        if (difficulty == Difficulty.EASY && score >= 70 && !concern) {
            return RouteDecision.NEXT_QUESTION;
        }

        // 5. 高分、稳定、已追问过：收尾换题
        if (score >= 85 && !hasWeakness && !concern && used >= 1) {
            return RouteDecision.NEXT_QUESTION;
        }

        // 6. 仅剩最后一次追问额度：能收尾则换题，否则普通追问
        if (remaining <= 1) {
            return (score >= 70 && !concern && !hasWeakness)
                    ? RouteDecision.NEXT_QUESTION
                    : RouteDecision.FOLLOW_UP;
        }

        // 7. 明显低分且有不足：直接普通追问，不消耗 LLM
        if (score < 50 && hasWeakness) {
            return RouteDecision.FOLLOW_UP;
        }

        // 8. 其余交给 LLM 精细决策
        return null;
    }
}
