package com.zunff.interview.agent.nodes;

import com.zunff.interview.constant.InterviewRound;
import com.zunff.interview.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 轮次切换节点
 * 负责检查轮次完成状态、提前结束判断、轮次切换
 *
 * 路由决策已移至 RoundTransitionRouter
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoundTransitionNode {

    /**
     * 执行轮次切换检查
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始轮次切换检查，当前轮次: {}", state.currentRound());

        Map<String, Object> updates = new HashMap<>();

        InterviewRound currentRound = state.currentRoundEnum();
        int consecutiveHighScores = state.consecutiveHighScores();

        // 检查是否可以提前结束面试
        if (state.canEndInterviewEarly() && state.isBusinessRound()) {
            log.info("检测到连续 {} 次高分，提前结束面试", consecutiveHighScores);
            updates.put(InterviewState.IS_FINISHED, true);
            return CompletableFuture.completedFuture(updates);
        }

        // 技术轮完成检查
        if (currentRound.isTechnical() && state.isTechnicalRoundComplete()) {
            double avgScore = state.technicalAverageScore();
            int passScore = state.roundPassScore();

            if (avgScore >= passScore) {
                log.info("技术轮完成，平均分 {} >= 通过分 {}，切换到业务轮", avgScore, passScore);
                updates.put(InterviewState.CURRENT_ROUND, InterviewRound.BUSINESS.getCode());
                updates.put(InterviewState.QUESTION_INDEX, 0);
                updates.put(InterviewState.FOLLOW_UP_COUNT, 0);
            } else {
                log.info("技术轮平均分 {} < 通过分 {}，继续技术轮", avgScore, passScore);
            }
        }

        // 业务轮完成检查
        if (currentRound.isBusiness() && state.isBusinessRoundComplete()) {
            log.info("业务轮完成，准备生成报告");
            updates.put(InterviewState.IS_FINISHED, true);
        }

        return CompletableFuture.completedFuture(updates);
    }
}
