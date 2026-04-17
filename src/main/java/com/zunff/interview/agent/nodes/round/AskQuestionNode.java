package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.model.dto.GeneratedQuestion;
import com.zunff.interview.model.websocket.QuestionMessage;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.websocket.InterviewWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 提问节点
 * 从题目队列中获取下一个问题，添加到问题列表，并推送给前端
 */
@Slf4j
@Component
public class AskQuestionNode {

    private final InterviewWebSocketHandler webSocketHandler;

    public AskQuestionNode(@Lazy InterviewWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        // 检查是否为追问场景
        String decision = state.decision();
        if (decision != null && !RouteDecision.NEXT_QUESTION.getValue().equals(decision)) {
            // 追问场景：CURRENT_QUESTION 已由追问节点设置，直接推送问题
            String followUpQuestion = state.currentQuestion();
            String questionType = state.questionType();
            int questionIndex = state.questionIndex();

            log.info("[{}] 追问场景，直接推送追问问题: {}", state.currentRoundEnum().getDisplayName(), followUpQuestion);

            // 推送追问问题到前端
            webSocketHandler.sendQuestion(state.sessionId(), QuestionMessage.builder()
                    .content(followUpQuestion)
                    .questionType(questionType)
                    .questionIndex(questionIndex)
                    .isFollowUp(true)
                    .build());

            log.info("已推送追问问题到前端: [{}] {}", questionType, followUpQuestion);

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.QUESTIONS, followUpQuestion);
            // 清空决策状态，准备接收回答
            updates.put(InterviewState.DECISION, null);
            return CompletableFuture.completedFuture(updates);
        }

        // 正常场景：从队列获取下一题
        GeneratedQuestion nextQuestion;
        int newIndex;

        // 根据当前轮次从队列获取题目
        if (state.isTechnicalRound()) {
            if (!state.hasMoreTechnicalQuestions()) {
                log.warn("技术轮题目队列为空");
                // 队列为空，标记轮次结束
                Map<String, Object> updates = new HashMap<>();
                updates.put(InterviewState.DECISION, RouteDecision.NEXT_QUESTION.getValue());
                return CompletableFuture.completedFuture(updates);
            }
            nextQuestion = state.peekNextTechnicalQuestion();
            newIndex = state.currentTechnicalIndex() + 1;
        } else {
            if (!state.hasMoreBusinessQuestions()) {
                log.warn("业务轮题目队列为空");
                // 队列为空，标记轮次结束
                Map<String, Object> updates = new HashMap<>();
                updates.put(InterviewState.DECISION, RouteDecision.NEXT_QUESTION.getValue());
                return CompletableFuture.completedFuture(updates);
            }
            nextQuestion = state.peekNextBusinessQuestion();
            newIndex = state.currentBusinessIndex() + 1;
        }

        if (nextQuestion == null) {
            log.error("从队列获取题目失败");
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.DECISION, RouteDecision.NEXT_QUESTION.getValue());
            return CompletableFuture.completedFuture(updates);
        }

        log.info("[{}] 提问 [{}] {}", state.currentRoundEnum().getDisplayName(), nextQuestion.getQuestionIndex(), nextQuestion.getQuestion());

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.QUESTIONS, nextQuestion.getQuestion());
        updates.put(InterviewState.CURRENT_QUESTION, nextQuestion.getQuestion());
        updates.put(InterviewState.QUESTION_TYPE, nextQuestion.getQuestionType());
        updates.put(InterviewState.QUESTION_INDEX, nextQuestion.getQuestionIndex());
        updates.put(InterviewState.CURRENT_GENERATED_QUESTION, nextQuestion); // 保存完整对象

        // 清空追问次数，开始新问题
        updates.put(InterviewState.FOLLOW_UP_COUNT, 0);
        updates.put(InterviewState.FOLLOW_UP_CHAIN, new ArrayList<>()); // 清空追问链路
        log.info("新问题开始，清空追问次数: {} -> 0", state.followUpCount());

        // 更新对应轮次的索引
        if (state.isTechnicalRound()) {
            updates.put(InterviewState.CURRENT_TECHNICAL_INDEX, newIndex);
        } else {
            updates.put(InterviewState.CURRENT_BUSINESS_INDEX, newIndex);
        }

        // 推送问题到前端
        webSocketHandler.sendQuestion(state.sessionId(), QuestionMessage.builder()
                .content(nextQuestion.getQuestion())
                .questionType(nextQuestion.getQuestionType())
                .questionIndex(nextQuestion.getQuestionIndex())
                .build());

        log.info("已推送问题到前端: [{}] {}", nextQuestion.getQuestionType(), nextQuestion.getQuestion());

        return CompletableFuture.completedFuture(updates);
    }
}
