package com.zunff.interview.agent.router;

import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.state.InterviewState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 评估后路由器
 * 决定是否需要追问
 */
@Slf4j
@Component
public class EvaluationRouter {

    /**
     * 路由决策
     * @return FOLLOW_UP 或 NEXT_QUESTION
     */
    public String route(InterviewState state) {
        boolean needFollowUp = state.needFollowUp();
        int followUpCount = state.followUpCount();
        int maxFollowUps = state.maxFollowUpsForCurrentRound();

        String result = (needFollowUp && followUpCount < maxFollowUps)
                ? RouteDecision.FOLLOW_UP.getValue()
                : RouteDecision.NEXT_QUESTION.getValue();

        log.debug("[{}] 子图路由决策: {}", state.currentRoundEnum().getDisplayName(), result);
        return result;
    }
}
