package com.zunff.interview.agent.nodes;

import com.zunff.interview.state.InterviewState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 等待回答节点
 * 设置等待状态标志，让主图知道需要暂停
 */
@Slf4j
@Component
public class WaitForAnswerNode {

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("等待候选人回答问题 {}", state.questionIndex());

        Map<String, Object> updates = new HashMap<>();

        String answerText = state.answerText();
        boolean hasAnswer = answerText != null && !answerText.isEmpty();

        if (!hasAnswer) {
            // 没有答案，设置等待状态
            updates.put(InterviewState.WAITING_FOR_ANSWER, true);
            log.debug("设置等待状态: WAITING_FOR_ANSWER=true");
        } else {
            // 有答案，清除等待状态
            updates.put(InterviewState.WAITING_FOR_ANSWER, false);
            log.debug("清除等待状态: WAITING_FOR_ANSWER=false");
        }

        return CompletableFuture.completedFuture(updates);
    }
}
