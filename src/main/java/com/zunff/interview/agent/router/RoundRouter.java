package com.zunff.interview.agent.router;

import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.agent.state.InterviewState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 轮次路由器
 * 负责子图级别的路由决策：追问策略 + 轮次完成检查
 */
@Slf4j
@Component
public class RoundRouter {

    /**
     * 路由决策
     * 1. 追问上限检查（保护逻辑）
     * 2. 轮次完成检查
     *
     * @return FOLLOW_UP / DEEP_DIVE / CHALLENGE_MODE / NEXT_QUESTION / ROUND_COMPLETE
     */
    public String route(InterviewState state) {
        String decision = state.decision();
        int followUpCount = state.followUpCount();
        int maxFollowUps = state.maxFollowUpsForCurrentRound();

        // 1. 追问上限检查（保护逻辑）
        if (isFollowUpDecision(decision) && followUpCount >= maxFollowUps) {
            log.info("[{}] 追问次数已达上限 {}/{}，强制进入下一题",
                    state.currentRoundEnum().getDisplayName(), followUpCount, maxFollowUps);
            decision = RouteDecision.NEXT_QUESTION.getValue();
        }

        // 2. 新增：追问质量检查（防止低质量追问）
        if (isFollowUpDecision(decision) && shouldStopForQuality(state)) {
            log.info("[{}] 追问质量未改善，强制进入下一题",
                    state.currentRoundEnum().getDisplayName());
            decision = RouteDecision.NEXT_QUESTION.getValue();
        }

        // 3. 轮次完成检查
        if (RouteDecision.NEXT_QUESTION.getValue().equals(decision)) {
            if (isCurrentRoundComplete(state)) {
                log.info("[{}] 当前轮次题目已全部完成",
                        state.currentRoundEnum().getDisplayName());
                return RouteDecision.ROUND_COMPLETE.getValue();
            }
        }

        log.debug("[{}] 子图路由决策: {}", state.currentRoundEnum().getDisplayName(), decision);
        return decision;
    }

    /**
     * 判断是否为追问类决策
     */
    private boolean isFollowUpDecision(String decision) {
        return RouteDecision.FOLLOW_UP.getValue().equals(decision)
                || RouteDecision.DEEP_DIVE.getValue().equals(decision)
                || RouteDecision.CHALLENGE_MODE.getValue().equals(decision);
    }

    /**
     * 检查当前轮次是否完成
     */
    private boolean isCurrentRoundComplete(InterviewState state) {
        return state.isTechnicalRound()
                ? state.isTechnicalRoundComplete()
                : state.isBusinessRoundComplete();
    }

    /**
     * 新增：检查追问质量是否未改善（应该停止追问）
     */
    private boolean shouldStopForQuality(InterviewState state) {
        var chain = state.followUpChain();
        if (chain == null || chain.size() < 2) {
            return false; // 数据不足，不强制停止
        }

        // 检查最近2次追问是否改善了质量
        int recentScore = chain.get(chain.size() - 1).getOverallScore();
        int prevScore = chain.get(chain.size() - 2).getOverallScore();

        // 如果连续2次未改善（提升<5分），停止追问
        return recentScore - prevScore < 5;
    }
}
