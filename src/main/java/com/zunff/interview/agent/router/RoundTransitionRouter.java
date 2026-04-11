package com.zunff.interview.agent.router;

import com.zunff.interview.constant.InterviewRound;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.state.InterviewState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 轮次切换路由器
 * 决定面试流程的下一步动作
 */
@Slf4j
@Component
public class RoundTransitionRouter {

    /**
     * 路由决策
     * @return TECHNICAL_TO_BUSINESS / BUSINESS_DONE / CONTINUE / EARLY_END
     */
    public String route(InterviewState state) {
        InterviewRound currentRound = state.currentRoundEnum();

        // 提前结束检测
        if (state.canEndInterviewEarly()) {
            if (state.isBusinessRound()) {
                log.debug("路由决策: earlyEnd (业务轮连续高分)");
                return RouteDecision.EARLY_END.getValue();
            } else if (state.isTechnicalRound() && state.isTechnicalRoundComplete()) {
                log.debug("路由决策: technicalToBusiness (技术轮高分提前完成)");
                return RouteDecision.TECHNICAL_TO_BUSINESS.getValue();
            }
        }

        // 技术轮完成检查
        if (currentRound.isTechnical()) {
            if (state.isTechnicalRoundComplete()) {
                double avgScore = state.technicalAverageScore();
                if (avgScore >= state.roundPassScore()) {
                    log.debug("路由决策: technicalToBusiness");
                    return RouteDecision.TECHNICAL_TO_BUSINESS.getValue();
                }
            }
            log.debug("路由决策: continue (技术轮继续)");
            return RouteDecision.CONTINUE.getValue();
        }

        // 业务轮完成检查
        if (currentRound.isBusiness()) {
            if (state.isBusinessRoundComplete()) {
                log.debug("路由决策: businessDone");
                return RouteDecision.BUSINESS_DONE.getValue();
            }
            log.debug("路由决策: continue (业务轮继续)");
            return RouteDecision.CONTINUE.getValue();
        }

        log.debug("路由决策: continue (默认)");
        return RouteDecision.CONTINUE.getValue();
    }
}
