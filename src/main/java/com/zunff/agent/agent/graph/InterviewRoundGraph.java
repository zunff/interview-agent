package com.zunff.agent.agent.graph;

import com.zunff.agent.agent.nodes.AnswerEvaluatorNode;
import com.zunff.agent.agent.nodes.AskQuestionNode;
import com.zunff.agent.agent.nodes.FollowUpDecisionNode;
import com.zunff.agent.agent.nodes.GenerateFollowUpNode;
import com.zunff.agent.agent.nodes.QuestionGeneratorNode;
import com.zunff.agent.agent.nodes.WaitForAnswerNode;
import com.zunff.agent.agent.router.EvaluationRouter;
import com.zunff.agent.constant.InterviewRound;
import com.zunff.agent.constant.NodeNames;
import com.zunff.agent.constant.RouteDecision;
import com.zunff.agent.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 面试轮次子图工厂
 * 创建可复用的面试轮次子图（技术轮/业务轮）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewRoundGraph {

    private final QuestionGeneratorNode questionGeneratorNode;
    private final AnswerEvaluatorNode answerEvaluatorNode;
    private final FollowUpDecisionNode followUpDecisionNode;
    private final AskQuestionNode askQuestionNode;
    private final WaitForAnswerNode waitForAnswerNode;
    private final GenerateFollowUpNode generateFollowUpNode;
    private final EvaluationRouter evaluationRouter;

    /**
     * 创建轮次子图
     * @param round 轮次类型
     * @return 未编译的子图
     */
    public StateGraph<InterviewState> createGraph(InterviewRound round) throws GraphStateException {
        log.info("创建轮次子图: {}", round.getDisplayName());

        String prefix = round.isTechnical() ? NodeNames.TECH_PREFIX : NodeNames.BIZ_PREFIX;
        String generateQuestion = prefix + NodeNames.GENERATE_QUESTION;
        String askQuestion = prefix + NodeNames.ASK_QUESTION;
        String waitForAnswer = prefix + NodeNames.WAIT_FOR_ANSWER;
        String evaluateAnswer = prefix + NodeNames.EVALUATE_ANSWER;
        String followUpDecision = prefix + NodeNames.FOLLOW_UP_DECISION;
        String generateFollowUp = prefix + NodeNames.GENERATE_FOLLOW_UP;

        return new StateGraph<>(InterviewState.SCHEMA, InterviewState::new)
                .addNode(generateQuestion, questionGeneratorNode::execute)
                .addNode(askQuestion, askQuestionNode::execute)
                .addNode(waitForAnswer, waitForAnswerNode::execute)
                .addNode(evaluateAnswer, answerEvaluatorNode::execute)
                .addNode(followUpDecision, followUpDecisionNode::execute)
                .addNode(generateFollowUp, generateFollowUpNode::execute)

                .addEdge(START, generateQuestion)
                .addEdge(generateQuestion, askQuestion)
                .addEdge(askQuestion, waitForAnswer)
                .addEdge(waitForAnswer, evaluateAnswer)
                .addEdge(evaluateAnswer, followUpDecision)

                .addConditionalEdges(
                        followUpDecision,
                        state -> CompletableFuture.completedFuture(evaluationRouter.route(state)),
                        Map.of(
                                RouteDecision.FOLLOW_UP.getValue(), generateFollowUp,
                                RouteDecision.NEXT_QUESTION.getValue(), END
                        )
                )
                .addEdge(generateFollowUp, askQuestion);
    }

    /**
     * 获取等待回答节点名称
     */
    public static String getWaitForAnswerNodeName(InterviewRound round) {
        String prefix = round.isTechnical() ? NodeNames.TECH_PREFIX : NodeNames.BIZ_PREFIX;
        return prefix + NodeNames.WAIT_FOR_ANSWER;
    }
}
