package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.model.bo.GeneratedQuestion;
import com.zunff.interview.model.websocket.QuestionMessage;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.websocket.InterviewWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 提问节点
 * 从题目队列队头消费题目，根据 questionType 判断是否为追问
 */
@Slf4j
@Component
public class AskQuestionNode {

    private final InterviewWebSocketHandler webSocketHandler;

    public AskQuestionNode(@Lazy InterviewWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        // 根据当前轮次获取对应队列
        String queueKey;
        if (state.isTechnicalRound()) {
            queueKey = InterviewState.TECHNICAL_QUESTIONS_QUEUE;
        } else {
            queueKey = InterviewState.BUSINESS_QUESTIONS_QUEUE;
        }

        List<GeneratedQuestion> queue = new ArrayList<>(state.isTechnicalRound()
                ? state.technicalQuestionsQueue()
                : state.businessQuestionsQueue());

        if (queue.isEmpty()) {
            log.warn("[{}] 题目队列为空，轮次结束", state.currentRoundEnum().getDisplayName());
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.DECISION, RouteDecision.NEXT_QUESTION.getValue());
            return CompletableFuture.completedFuture(updates);
        }

        // 从队头弹出题目（消费）
        GeneratedQuestion nextQuestion = queue.removeFirst();

        // 根据 questionType 判断是否为追问
        QuestionType type = QuestionType.fromDisplayName(nextQuestion.getQuestionType());
        boolean isFollowUp = type.isFollowUpType();

        log.info("[{}] {} [{}] {}", state.currentRoundEnum().getDisplayName(),
                isFollowUp ? "追问" : "提问",
                nextQuestion.getQuestionIndex(), nextQuestion.getQuestion());

        Map<String, Object> updates = new HashMap<>();
        updates.put(queueKey, queue);  // 更新队列（消费后的列表）
        updates.put(InterviewState.CURRENT_GENERATED_QUESTION, nextQuestion);

        if (!isFollowUp) {
            // 主问题：更新主问题状态，清空追问次数和链路
            updates.put(InterviewState.MAIN_GENERATED_QUESTION, nextQuestion);
            updates.put(InterviewState.FOLLOW_UP_COUNT, 0);
            updates.put(InterviewState.FOLLOW_UP_CHAIN, new ArrayList<>());
            log.info("新问题开始，清空追问次数: {} -> 0", state.followUpCount());
        } else {
            // 追问：累加追问次数
            updates.put(InterviewState.FOLLOW_UP_COUNT, state.followUpCount() + 1);
            log.info("追问次数累加: {} -> {}", state.followUpCount(), state.followUpCount() + 1);
        }

        // 推送问题到前端
        webSocketHandler.sendQuestion(state.sessionId(), QuestionMessage.builder()
                .content(nextQuestion.getQuestion())
                .questionType(nextQuestion.getQuestionType())
                .questionIndex(nextQuestion.getQuestionIndex())
                .isFollowUp(isFollowUp)
                .build());

        log.info("已推送{}到前端: [{}] {}", isFollowUp ? "追问问题" : "问题",
                nextQuestion.getQuestionType(), nextQuestion.getQuestion());

        return CompletableFuture.completedFuture(updates);
    }
}
