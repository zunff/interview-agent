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
 * 面试初始化节点
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitInterviewNode {

    /**
     * 执行面试初始化
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("初始化面试，会话ID: {}", state.sessionId());

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.QUESTION_INDEX, 0);
        updates.put(InterviewState.FOLLOW_UP_COUNT, 0);
        updates.put(InterviewState.IS_FINISHED, false);

        // 初始化轮次状态
        updates.put(InterviewState.CURRENT_ROUND, InterviewRound.TECHNICAL.getCode());
        updates.put(InterviewState.TECHNICAL_QUESTIONS_DONE, 0);
        updates.put(InterviewState.BUSINESS_QUESTIONS_DONE, 0);
        updates.put(InterviewState.CONSECUTIVE_HIGH_SCORES, 0);

        // 设置默认配置
        if (state.data().get(InterviewState.MAX_QUESTIONS) == null) {
            updates.put(InterviewState.MAX_QUESTIONS, 10);
        }
        if (state.data().get(InterviewState.MAX_FOLLOW_UPS) == null) {
            updates.put(InterviewState.MAX_FOLLOW_UPS, 2);
        }

        return CompletableFuture.completedFuture(updates);
    }
}
