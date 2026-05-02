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
                    maxFollowUps,
                    state.followUpChain()
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

        // 1. 已达追问上限 → NEXT_QUESTION
        if (used >= max) {
            return RouteDecision.NEXT_QUESTION;
        }

        // 2. 优化：低分（<60分）且有弱点 → DEEP_DIVE
        if (score < 60 && hasWeakness && remaining >= 2) {
            return RouteDecision.DEEP_DIVE;
        }

        // 3. 优化：高分（>75分）且无弱点 → CHALLENGE_MODE（放宽条件，移除used==0限制）
        if (score > 75 && !hasWeakness && remaining >= 1) {
            return RouteDecision.CHALLENGE_MODE;
        }

        // 4. 优化：表现优秀（>=80分）且无弱点 → NEXT_QUESTION
        if (score >= 80 && !hasWeakness) {
            return RouteDecision.NEXT_QUESTION;
        }

        // 5. 新增：追问质量未改善 → NEXT_QUESTION
        if (!shouldContinueFollowUp(eval, state)) {
            log.info("追问质量未改善，进入下一题");
            return RouteDecision.NEXT_QUESTION;
        }

        // 6. 仅剩1次追问时，有模态异常 → FOLLOW_UP
        if (remaining == 1 && concern) {
            return RouteDecision.FOLLOW_UP;
        }

        // 7. 仅剩1次追问时，分数>=70且无弱点 → NEXT_QUESTION
        if (remaining == 1 && score >= 70 && !hasWeakness) {
            return RouteDecision.NEXT_QUESTION;
        }

        // 8. 优化：中等分数（60-80）且有弱点 → 交给 LLM 精细决策（扩大范围）
        if (score >= 60 && score <= 80 && hasWeakness && !concern) {
            return null;
        }

        // 9. 低分且无弱点 → LLM 决策（可能需要 deepDive）
        if (score < 60 && !hasWeakness) {
            return null;
        }

        // 10. 有弱点且分数不太低 → 交给 LLM 决策（避免直接 followUp）
        if (hasWeakness && score >= 60) {
            return null;
        }

        // 11. 有模态异常 → FOLLOW_UP（仅模态异常场景）
        if (concern) {
            return RouteDecision.FOLLOW_UP;
        }

        // 12. 其他 → LLM 精细决策
        return null;
    }

    /**
     * 新增：检查是否应该继续追问（基于质量改善评估）
     */
    private boolean shouldContinueFollowUp(EvaluationBO eval, InterviewState state) {
        // 获取追问历史
        var chain = state.followUpChain();
        if (chain == null || chain.size() < 2) {
            return true; // 首次追问或数据不足，继续
        }

        // 检查最近2次追问的分数趋势
        int recentScore = eval.getOverallScore();
        int prevScore = chain.get(chain.size() - 1).getOverallScore();

        // 如果连续2次分数未提升（提升<5分），则停止追问
        if (recentScore - prevScore < 5) {
            log.info("连续追问质量未改善: 前次={}, 当前={}, 提升={}",
                    prevScore, recentScore, recentScore - prevScore);
            return false;
        }

        return true;
    }
}
