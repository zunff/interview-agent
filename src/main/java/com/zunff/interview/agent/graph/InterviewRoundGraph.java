package com.zunff.interview.agent.graph;

import com.zunff.interview.agent.nodes.AggregateAnalysisNode;
import com.zunff.interview.agent.nodes.AskQuestionNode;
import com.zunff.interview.agent.nodes.AudioAnalysisNode;
import com.zunff.interview.agent.nodes.ChallengeQuestionNode;
import com.zunff.interview.agent.nodes.DeepDiveNode;
import com.zunff.interview.agent.nodes.FollowUpDecisionNode;
import com.zunff.interview.agent.nodes.GenerateFollowUpNode;
import com.zunff.interview.agent.nodes.QuestionGeneratorNode;
import com.zunff.interview.agent.nodes.VisionAnalysisNode;
import com.zunff.interview.agent.nodes.WaitForAnswerNode;
import com.zunff.interview.agent.router.EvaluationRouter;
import com.zunff.interview.config.GraphConfigProperties;
import com.zunff.interview.constant.InterviewRound;
import com.zunff.interview.constant.NodeNames;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.state.InterviewState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 面试轮次子图工厂
 * 创建可复用的面试轮次子图（技术轮/业务轮）
 *
 * 架构改进：
 * - 使用条件路由处理"等待答案"逻辑
 * - waitForAnswer 后检查是否有答案，没有则返回 END（等待外部输入）
 * - 有答案则继续执行并行分析，完成后更新问题计数
 */
@Slf4j
@Component
public class InterviewRoundGraph {

    private final QuestionGeneratorNode questionGeneratorNode;
    private final AskQuestionNode askQuestionNode;
    private final WaitForAnswerNode waitForAnswerNode;
    private final GenerateFollowUpNode generateFollowUpNode;
    private final EvaluationRouter evaluationRouter;

    // 新增：并行分析节点
    private final VisionAnalysisNode visionAnalysisNode;
    private final AudioAnalysisNode audioAnalysisNode;
    private final AggregateAnalysisNode aggregateAnalysisNode;

    // 新增：条件分支节点
    private final FollowUpDecisionNode followUpDecisionNode;
    private final ChallengeQuestionNode challengeQuestionNode;
    private final DeepDiveNode deepDiveNode;

    private final GraphConfigProperties graphConfig;

    public InterviewRoundGraph(
            QuestionGeneratorNode questionGeneratorNode,
            AskQuestionNode askQuestionNode,
            WaitForAnswerNode waitForAnswerNode,
            GenerateFollowUpNode generateFollowUpNode,
            EvaluationRouter evaluationRouter,
            VisionAnalysisNode visionAnalysisNode,
            AudioAnalysisNode audioAnalysisNode,
            AggregateAnalysisNode aggregateAnalysisNode,
            FollowUpDecisionNode followUpDecisionNode,
            ChallengeQuestionNode challengeQuestionNode,
            DeepDiveNode deepDiveNode,
            GraphConfigProperties graphConfig) {
        this.questionGeneratorNode = questionGeneratorNode;
        this.askQuestionNode = askQuestionNode;
        this.waitForAnswerNode = waitForAnswerNode;
        this.generateFollowUpNode = generateFollowUpNode;
        this.evaluationRouter = evaluationRouter;
        this.visionAnalysisNode = visionAnalysisNode;
        this.audioAnalysisNode = audioAnalysisNode;
        this.aggregateAnalysisNode = aggregateAnalysisNode;
        this.followUpDecisionNode = followUpDecisionNode;
        this.challengeQuestionNode = challengeQuestionNode;
        this.deepDiveNode = deepDiveNode;
        this.graphConfig = graphConfig;
    }

    /**
     * 创建轮次子图
     * @param round 轮次类型
     * @return 编译后的子图
     */
    public CompiledGraph<InterviewState> createGraph(InterviewRound round) throws GraphStateException {
        log.info("创建轮次子图: {}", round.getDisplayName());

        String prefix = round.isTechnical() ? NodeNames.TECH_PREFIX : NodeNames.BIZ_PREFIX;
        String generateQuestion = prefix + NodeNames.GENERATE_QUESTION;
        String askQuestion = prefix + NodeNames.ASK_QUESTION;
        String waitForAnswer = prefix + NodeNames.WAIT_FOR_ANSWER;
        String followUpDecision = prefix + NodeNames.FOLLOW_UP_DECISION;
        String generateFollowUp = prefix + NodeNames.GENERATE_FOLLOW_UP;

        // 并行分析节点
        String analyzeVision = prefix + "analyzeVision";
        String analyzeAudio = prefix + "analyzeAudio";
        String aggregateAnalysis = prefix + "aggregateAnalysis";

        // 条件分支节点
        String generateChallenge = prefix + "generateChallenge";
        String generateDeepDive = prefix + "generateDeepDive";

        return new StateGraph<>(InterviewState.SCHEMA, InterviewState::new)
                // ========== 添加节点 ==========
                .addNode(generateQuestion, questionGeneratorNode::execute)
                .addNode(askQuestion, askQuestionNode::execute)
                .addNode(waitForAnswer, waitForAnswerNode::execute)

                // 并行分析节点
                .addNode(analyzeVision, visionAnalysisNode::execute)
                .addNode(analyzeAudio, audioAnalysisNode::execute)
                .addNode(aggregateAnalysis, aggregateAnalysisNode::execute)

                // 条件分支节点
                .addNode(followUpDecision, followUpDecisionNode::execute)
                .addNode(generateFollowUp, generateFollowUpNode::execute)
                .addNode(generateChallenge, challengeQuestionNode::execute)
                .addNode(generateDeepDive, deepDiveNode::execute)

                // ========== 定义边 ==========
                .addEdge(START, generateQuestion)
                .addEdge(generateQuestion, askQuestion)
                .addEdge(askQuestion, waitForAnswer)

                // ========== 条件路由：检查是否有答案 ==========
                // 如果有答案，继续分析；如果没有答案，返回 END 等待
                .addConditionalEdges(
                        waitForAnswer,
                        state -> CompletableFuture.completedFuture(hasAnswer(state)),
                        Map.of(
                                "has_answer", analyzeVision,
                                "waiting", END
                        )
                )

                // 并行分支：视觉分析后进入聚合
                .addEdge(analyzeVision, aggregateAnalysis)
                // 音频分析也进入聚合
                .addEdge(analyzeAudio, aggregateAnalysis)

                // 聚合后进行追问决策
                .addEdge(aggregateAnalysis, followUpDecision)

                // ========== 条件路由：追问决策 ==========
                .addConditionalEdges(
                        followUpDecision,
                        state -> CompletableFuture.completedFuture(routeAfterEvaluation(state, round)),
                        Map.of(
                                RouteDecision.FOLLOW_UP.getValue(), generateFollowUp,
                                RouteDecision.DEEP_DIVE.getValue(), generateDeepDive,
                                RouteDecision.CHALLENGE_MODE.getValue(), generateChallenge,
                                RouteDecision.NEXT_QUESTION.getValue(), END
                        )
                )

                // 所有分支最终回到 askQuestion 形成循环
                .addEdge(generateFollowUp, askQuestion)
                .addEdge(generateChallenge, askQuestion)
                .addEdge(generateDeepDive, askQuestion)

                .compile(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())
                        .recursionLimit(graphConfig.getSubRecursionLimit())
                        .build());
    }

    /**
     * 检查状态中是否有答案
     */
    private String hasAnswer(InterviewState state) {
        String answerText = state.answerText();
        boolean hasAnswer = answerText != null && !answerText.isEmpty();
        log.debug("检查答案状态: hasAnswer={}, answerText={}", hasAnswer,
                hasAnswer ? answerText.substring(0, Math.min(30, answerText.length())) + "..." : "null");
        return hasAnswer ? "has_answer" : "waiting";
    }

    /**
     * 评估后的路由决策，同时更新问题计数
     */
    private String routeAfterEvaluation(InterviewState state, InterviewRound round) {
        String decision = evaluationRouter.route(state);

        // 如果是进入下一题，更新问题计数
        if (RouteDecision.NEXT_QUESTION.getValue().equals(decision)) {
            // 注意：这里不能直接修改 state，需要通过状态更新
            // 但是在条件路由中，我们只能返回路由决策
            // 所以需要在 followUpDecision 节点中更新计数
            log.info("[{}] 问题完成，准备进入下一题", round.getDisplayName());
        }

        return decision;
    }
}
