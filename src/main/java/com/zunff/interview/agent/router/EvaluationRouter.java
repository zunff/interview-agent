package com.zunff.interview.agent.router;

import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.state.InterviewState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 评估后路由器
 * 决定是否需要追问、深入追问或挑战问题
 */
@Slf4j
@Component
public class EvaluationRouter {

    /**
     * 路由决策
     * @return FOLLOW_UP / DEEP_DIVE / CHALLENGE_MODE / NEXT_QUESTION
     */
    public String route(InterviewState state) {
        EvaluationBO evaluation = state.getCurrentEvaluation();
        int followUpCount = state.followUpCount();
        int maxFollowUps = state.maxFollowUpsForCurrentRound();

        int score = evaluation != null ? evaluation.getOverallScore() : 60;
        boolean needFollowUp = state.needFollowUp();

        String result;

        // 低分深入追问（仅在首次追问时触发，避免无限追问）
        if (score < 50 && followUpCount == 0) {
            result = RouteDecision.DEEP_DIVE.getValue();
            log.debug("[{}] 子图路由决策: {} (得分: {})",
                    state.currentRoundEnum().getDisplayName(), result, score);
            return result;
        }

        // 高分挑战模式（仅在首次追问时触发）
        if (score > 90 && followUpCount == 0) {
            result = RouteDecision.CHALLENGE_MODE.getValue();
            log.debug("[{}] 子图路由决策: {} (得分: {})",
                    state.currentRoundEnum().getDisplayName(), result, score);
            return result;
        }

        // 普通追问
        if (needFollowUp && followUpCount < maxFollowUps) {
            result = RouteDecision.FOLLOW_UP.getValue();
            log.debug("[{}] 子图路由决策: {} (追问次数: {}/{})",
                    state.currentRoundEnum().getDisplayName(), result, followUpCount, maxFollowUps);
            return result;
        }

        // 进入下一题
        result = RouteDecision.NEXT_QUESTION.getValue();
        log.debug("[{}] 子图路由决策: {}", state.currentRoundEnum().getDisplayName(), result);
        return result;
    }
}
