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
import com.zunff.interview.agent.router.EvaluationRouter;
import com.zunff.interview.config.GraphConfigProperties;
import com.zunff.interview.constant.InterviewRound;
import com.zunff.interview.constant.NodeNames;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.state.InterviewState;
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
 *
 * 架构：
 * - 返回未编译的 StateGraph，由主图统一编译
 * - 主图使用 addSubGraph() 合并子图，状态共享
 */
@Slf4j
@Component
public class InterviewRoundGraph {

    private final QuestionGeneratorNode questionGeneratorNode;
    private final AskQuestionNode askQuestionNode;
    private final GenerateFollowUpNode generateFollowUpNode;
    private final EvaluationRouter evaluationRouter;

    // 并行分析节点
    private final VisionAnalysisNode visionAnalysisNode;
    private final AudioAnalysisNode audioAnalysisNode;
    private final AggregateAnalysisNode aggregateAnalysisNode;

    // 条件分支节点
    private final FollowUpDecisionNode followUpDecisionNode;
    private final ChallengeQuestionNode challengeQuestionNode;
    private final DeepDiveNode deepDiveNode;

    public InterviewRoundGraph(
            QuestionGeneratorNode questionGeneratorNode,
            AskQuestionNode askQuestionNode,
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
        this.generateFollowUpNode = generateFollowUpNode;
        this.evaluationRouter = evaluationRouter;
        this.visionAnalysisNode = visionAnalysisNode;
        this.audioAnalysisNode = audioAnalysisNode;
        this.aggregateAnalysisNode = aggregateAnalysisNode;
        this.followUpDecisionNode = followUpDecisionNode;
        this.challengeQuestionNode = challengeQuestionNode;
        this.deepDiveNode = deepDiveNode;
    }

    /**
     * 创建轮次子图（返回未编译的 StateGraph）
     * 主图会使用 addSubGraph() 合并此子图
     *
     * @param round 轮次类型
     * @return 未编译的 StateGraph
     */
    public StateGraph<InterviewState> createGraph(InterviewRound round) throws GraphStateException {
        log.info("创建轮次子图: {}", round.getDisplayName());

        String prefix = round.isTechnical() ? NodeNames.TECH_PREFIX : NodeNames.BIZ_PREFIX;
        String generateQuestion = prefix + NodeNames.GENERATE_QUESTION;
        String askQuestion = prefix + NodeNames.ASK_QUESTION;
        String followUpDecision = prefix + NodeNames.FOLLOW_UP_DECISION;
        String generateFollowUp = prefix + NodeNames.GENERATE_FOLLOW_UP;

        // 并行分析节点
        String analyzeVision = prefix + NodeNames.ANALYZE_VISION;
        String analyzeAudio = prefix + NodeNames.ANALYZE_AUDIO;
        String aggregateAnalysis = prefix + NodeNames.AGGREGATE_ANALYSIS;

        // 条件分支节点
        String generateChallenge = prefix + NodeNames.GENERATE_CHALLENGE;
        String generateDeepDive = prefix + NodeNames.GENERATE_DEEP_DIVE;

        // 返回未编译的 StateGraph，由主图统一编译
        return new StateGraph<>(InterviewState.SCHEMA, InterviewState::new)
                // ========== 添加节点 ==========
                .addNode(generateQuestion, questionGeneratorNode::execute)
                .addNode(askQuestion, askQuestionNode::execute)

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
                // 恢复执行后进入视觉和音频分析（并行分支）
                .addEdge(askQuestion, analyzeVision)
                .addEdge(askQuestion, analyzeAudio)

                // 并行分支：两者都进入聚合节点
                .addEdge(analyzeVision, aggregateAnalysis)
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
                .addEdge(generateDeepDive, askQuestion);
        // 注意：不在这里 compile()，由主图统一编译
    }

    /**
     * 评估后的路由决策
     */
    private String routeAfterEvaluation(InterviewState state, InterviewRound round) {
        String decision = evaluationRouter.route(state);

        if (RouteDecision.NEXT_QUESTION.getValue().equals(decision)) {
            log.info("[{}] 问题完成，准备进入下一题", round.getDisplayName());
        }

        return decision;
    }
}
