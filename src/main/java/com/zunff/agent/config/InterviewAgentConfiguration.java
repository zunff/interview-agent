package com.zunff.agent.config;

import com.zunff.agent.agent.nodes.AnswerEvaluatorNode;
import com.zunff.agent.agent.nodes.FollowUpDecisionNode;
import com.zunff.agent.agent.nodes.QuestionGeneratorNode;
import com.zunff.agent.agent.nodes.ReportGeneratorNode;
import com.zunff.agent.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 面试 Agent 配置
 * 定义 LangGraph4j 状态图
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class InterviewAgentConfiguration {

    private final QuestionGeneratorNode questionGeneratorNode;
    private final AnswerEvaluatorNode answerEvaluatorNode;
    private final FollowUpDecisionNode followUpDecisionNode;
    private final ReportGeneratorNode reportGeneratorNode;

    /**
     * 创建面试 Agent 状态图
     */
    @Bean
    public CompiledGraph<InterviewState> interviewAgent() throws GraphStateException {
        log.info("初始化面试 Agent 状态图");

        return new StateGraph<>(InterviewState.SCHEMA, InterviewState::new)
                // ========== 添加节点 ==========
                .addNode("init", this::initInterview)
                .addNode("generateQuestion", questionGeneratorNode::execute)
                .addNode("askQuestion", this::askQuestion)
                .addNode("waitForAnswer", this::waitForAnswer)
                .addNode("evaluateAnswer", answerEvaluatorNode::execute)
                .addNode("followUpDecision", followUpDecisionNode::execute)
                .addNode("generateFollowUp", this::generateFollowUp)
                .addNode("nextQuestion", this::nextQuestion)
                .addNode("generateReport", reportGeneratorNode::execute)

                // ========== 定义边 ==========
                .addEdge(START, "init")
                .addEdge("init", "generateQuestion")
                .addEdge("generateQuestion", "askQuestion")
                .addEdge("askQuestion", "waitForAnswer")
                .addEdge("waitForAnswer", "evaluateAnswer")
                .addEdge("evaluateAnswer", "followUpDecision")

                // ========== 条件路由 ==========
                .addConditionalEdges(
                        "followUpDecision",
                        this::routeAfterEvaluationAsync,
                        Map.of(
                                "followUp", "generateFollowUp",
                                "nextQuestion", "nextQuestion",
                                "end", "generateReport"
                        )
                )
                .addEdge("generateFollowUp", "askQuestion")
                .addConditionalEdges(
                        "nextQuestion",
                        this::checkMoreQuestionsAsync,
                        Map.of(
                                "continue", "generateQuestion",
                                "end", "generateReport"
                        )
                )
                .addEdge("generateReport", END)

                // ========== 编译配置 ==========
                .compile(CompileConfig.builder()
                        .interruptBefore("waitForAnswer") // 在等待回答节点中断
                        .build());
    }

    /**
     * 初始化面试
     */
    private CompletableFuture<Map<String, Object>> initInterview(InterviewState state) {
        log.info("初始化面试，会话ID: {}", state.sessionId());

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.QUESTION_INDEX, 0);
        updates.put(InterviewState.FOLLOW_UP_COUNT, 0);
        updates.put(InterviewState.IS_FINISHED, false);

        // 设置默认配置
        if (state.data().get(InterviewState.MAX_QUESTIONS) == null) {
            updates.put(InterviewState.MAX_QUESTIONS, 10);
        }
        if (state.data().get(InterviewState.MAX_FOLLOW_UPS) == null) {
            updates.put(InterviewState.MAX_FOLLOW_UPS, 2);
        }

        return CompletableFuture.completedFuture(updates);
    }

    /**
     * 提问节点
     * 将问题推送给候选人（实际推送由 WebSocket 完成）
     */
    private CompletableFuture<Map<String, Object>> askQuestion(InterviewState state) {
        String question = state.currentQuestion();
        String questionType = state.questionType();
        int questionIndex = state.questionIndex();

        log.info("提问 [{}] {}: {}", questionType, questionIndex, question);

        // 将问题添加到问题列表
        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.QUESTIONS, question);

        return CompletableFuture.completedFuture(updates);
    }

    /**
     * 等待回答节点
     * 此节点会中断，等待外部调用 updateState 提交答案
     */
    private CompletableFuture<Map<String, Object>> waitForAnswer(InterviewState state) {
        log.info("等待候选人回答问题 {}", state.questionIndex());
        // 这个节点实际上会被中断，等待 updateState 恢复
        return CompletableFuture.completedFuture(new HashMap<>());
    }

    /**
     * 生成追问
     */
    private CompletableFuture<Map<String, Object>> generateFollowUp(InterviewState state) {
        String followUpQuestion = (String) state.data().get(InterviewState.FOLLOW_UP_QUESTION);

        log.info("生成追问: {}", followUpQuestion);

        Map<String, Object> updates = new HashMap<>();
        updates.put(InterviewState.CURRENT_QUESTION, followUpQuestion);
        updates.put(InterviewState.QUESTION_TYPE, "追问");

        return CompletableFuture.completedFuture(updates);
    }

    /**
     * 进入下一题
     */
    private CompletableFuture<Map<String, Object>> nextQuestion(InterviewState state) {
        int currentIndex = state.questionIndex();
        @SuppressWarnings("unchecked")
        Map<String, Object> currentEval = (Map<String, Object>) state.data().get(InterviewState.CURRENT_EVALUATION);

        log.info("进入下一题，当前题目数: {}", currentIndex);

        Map<String, Object> updates = new HashMap<>();
        // 保存当前评估结果
        if (currentEval != null) {
            updates.put(InterviewState.EVALUATIONS, currentEval);
        }
        // 重置追问计数
        updates.put(InterviewState.FOLLOW_UP_COUNT, 0);
        updates.put(InterviewState.NEED_FOLLOW_UP, false);

        return CompletableFuture.completedFuture(updates);
    }

    /**
     * 评估后的路由决策（异步版本）
     */
    private CompletableFuture<String> routeAfterEvaluationAsync(InterviewState state) {
        boolean needFollowUp = state.needFollowUp();
        int questionIndex = state.questionIndex();
        int maxQuestions = state.maxQuestions();
        int followUpCount = state.followUpCount();

        String result;
        if (needFollowUp && followUpCount < state.maxFollowUps()) {
            result = "followUp";
        } else if (questionIndex < maxQuestions) {
            result = "nextQuestion";
        } else {
            result = "end";
        }

        log.debug("路由决策: {}", result);
        return CompletableFuture.completedFuture(result);
    }

    /**
     * 检查是否还有更多问题（异步版本）
     */
    private CompletableFuture<String> checkMoreQuestionsAsync(InterviewState state) {
        int questionIndex = state.questionIndex();
        int maxQuestions = state.maxQuestions();

        String result;
        if (questionIndex < maxQuestions) {
            result = "continue";
        } else {
            result = "end";
        }

        log.debug("检查更多问题: {}", result);
        return CompletableFuture.completedFuture(result);
    }
}
