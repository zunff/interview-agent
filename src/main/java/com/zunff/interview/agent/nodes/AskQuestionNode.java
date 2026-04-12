package com.zunff.interview.agent.nodes;

import com.zunff.interview.model.websocket.QuestionMessage;
import com.zunff.interview.state.InterviewState;
import com.zunff.interview.websocket.InterviewWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 提问节点
 * 将当前问题添加到问题列表，并推送给前端
 */
@Slf4j
@Component
public class AskQuestionNode {

    private final InterviewWebSocketHandler webSocketHandler;

    public AskQuestionNode(@Lazy InterviewWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("[{}] 提问 [{}] {}",
                state.currentRoundEnum().getDisplayName(),
                state.questionIndex(),
                state.currentQuestion());

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.QUESTIONS, state.currentQuestion());

        // 推送问题到前端
        String questionType = state.questionType();
        webSocketHandler.sendQuestion(state.sessionId(), QuestionMessage.builder()
                .content(state.currentQuestion())
                .questionType(questionType)
                .questionIndex(state.questionIndex())
                .build());
        log.info("已推送问题到前端: [{}] {}", questionType, state.currentQuestion());
        return CompletableFuture.completedFuture(updates);
    }
}
