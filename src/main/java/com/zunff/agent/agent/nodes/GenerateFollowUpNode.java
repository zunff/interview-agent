package com.zunff.agent.agent.nodes;

import com.zunff.agent.constant.QuestionType;
import com.zunff.agent.state.InterviewState;
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
        return CompletableFuture.completedFuture(updates);
    }
}
