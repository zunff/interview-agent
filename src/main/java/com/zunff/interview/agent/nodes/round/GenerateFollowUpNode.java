package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.agent.state.InterviewState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 生成追问节点
 * 将追问问题设置为当前问题
 */
@Slf4j
@Component
public class GenerateFollowUpNode {

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        String followUpQuestion = (String) state.data().get(InterviewState.FOLLOW_UP_QUESTION);
        log.info("生成追问: {}", followUpQuestion);

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.CURRENT_QUESTION, followUpQuestion);
        updates.put(InterviewState.QUESTION_TYPE, QuestionType.FOLLOW_UP.getDisplayName());

        // 追问真正生成后才累加次数
        updates.put(InterviewState.FOLLOW_UP_COUNT, state.followUpCount() + 1);
        log.info("追问次数累加: {} -> {}", state.followUpCount(), state.followUpCount() + 1);

        return CompletableFuture.completedFuture(updates);
    }
}
