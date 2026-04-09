package com.zunff.agent.agent.nodes;

import com.zunff.agent.state.InterviewState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 提问节点
 * 将当前问题添加到问题列表
 */
@Slf4j
@Component
public class AskQuestionNode {

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("[{}] 提问 [{}] {}",
                state.currentRoundEnum().getDisplayName(),
                state.questionIndex(),
                state.currentQuestion());

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.QUESTIONS, state.currentQuestion());
        return CompletableFuture.completedFuture(updates);
    }
}
