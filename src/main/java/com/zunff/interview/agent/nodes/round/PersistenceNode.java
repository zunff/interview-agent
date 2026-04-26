package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.service.AnswerRecordService;
import com.zunff.interview.service.EvaluationRecordService;
import com.zunff.interview.agent.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 持久化节点
 * 负责将回答和评估结果保存到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistenceNode {

    private final AnswerRecordService answerRecordService;
    private final EvaluationRecordService evaluationRecordService;

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        String sessionId = state.sessionId();
        int questionIndex = state.questionIndex();
        String question = state.currentQuestion();
        String answerText = state.answerText();
        EvaluationBO evaluation = state.getCurrentEvaluation();

        log.info("持久化评估结果: sessionId={}, questionIndex={}, overallScore={}",
                sessionId, questionIndex, evaluation != null ? evaluation.getOverallScore() : "null");

        try {
            // 记录回答
            if (answerText != null && !answerText.isEmpty()) {
                answerRecordService.recordAnswer(sessionId, questionIndex, question, answerText);
            }

            // 保存评估结果
            if (evaluation != null) {
                evaluationRecordService.saveEvaluation(sessionId, evaluation);
            }

            log.info("持久化完成: sessionId={}, questionIndex={}", sessionId, questionIndex);

        } catch (Exception e) {
            log.error("持久化失败: sessionId={}, questionIndex={}", sessionId, questionIndex, e);
            // 不抛出异常，允许流程继续
        }

        return CompletableFuture.completedFuture(new HashMap<>());
    }
}
