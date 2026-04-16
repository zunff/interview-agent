package com.zunff.interview.agent.nodes.main;

import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.constant.InterviewRound;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 轮次切换节点
 * 技术轮结束后切换 currentRound 状态为业务轮
 */
@Slf4j
@Component
public class RoundTransitionNode {

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("技术轮完成，切换到业务轮");
        return CompletableFuture.completedFuture(Map.of(
                InterviewState.CURRENT_ROUND, InterviewRound.BUSINESS.getCode()
        ));
    }
}