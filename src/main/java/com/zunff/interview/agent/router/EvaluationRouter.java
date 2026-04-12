package com.zunff.interview.agent.router;

import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.state.InterviewState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 评估后路由器
 * 只负责保护性检查，信任 LLM 的决策
 */
@Slf4j
@Component
public class EvaluationRouter {

    /**
     * 路由决策
     * 读取 LLM 的决策，只做保护性检查
     * @return FOLLOW_UP / DEEP_DIVE / CHALLENGE_MODE / NEXT_QUESTION
     */
    public String route(InterviewState state) {
        String decision = state.decision();
        int followUpCount = state.followUpCount();
        int maxFollowUps = state.maxFollowUpsForCurrentRound();

        // 保护逻辑：追问上限检查
        if (isFollowUpDecision(decision) && followUpCount >= maxFollowUps) {
            log.info("[{}] 追问次数已达上限 {}/{}，强制进入下一题",
                    state.currentRoundEnum().getDisplayName(), followUpCount, maxFollowUps);
            return RouteDecision.NEXT_QUESTION.getValue();
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
}
