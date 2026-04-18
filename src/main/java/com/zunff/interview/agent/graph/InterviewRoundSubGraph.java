package com.zunff.interview.agent.graph;

import com.zunff.interview.agent.nodes.round.AskQuestionNode;
import com.zunff.interview.agent.nodes.round.ChallengeFollowUpGenNode;
import com.zunff.interview.agent.nodes.round.ComprehensiveEvaluationNode;
import com.zunff.interview.agent.nodes.round.DeepDiveFollowUpGenNode;
import com.zunff.interview.agent.nodes.round.FollowUpDecisionNode;
import com.zunff.interview.agent.nodes.round.BasicFollowUpGenNode;
import com.zunff.interview.agent.router.RoundRouter;
import com.zunff.interview.constant.InterviewRound;
import com.zunff.interview.agent.names.NodeNames;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.agent.state.InterviewState;
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
 * - 题目从队列中获取，不再动态生成（追问除外）
 */
@Slf4j
@Component
public class InterviewRoundSubGraph {

    private final AskQuestionNode askQuestionNode;
    private final BasicFollowUpGenNode basicFollowUpGenNode;
    private final RoundRouter roundRouter;

    // Omni多模态综合评估节点（替代并行三分支）
    private final ComprehensiveEvaluationNode comprehensiveEvaluationNode;

    // 条件分支节点
    private final FollowUpDecisionNode followUpDecisionNode;
    private final ChallengeFollowUpGenNode challengeFollowUpGenNode;
    private final DeepDiveFollowUpGenNode deepDiveFollowUpGenNode;

    public InterviewRoundSubGraph(
            AskQuestionNode askQuestionNode,
            BasicFollowUpGenNode basicFollowUpGenNode,
            RoundRouter roundRouter,
            ComprehensiveEvaluationNode comprehensiveEvaluationNode,
            FollowUpDecisionNode followUpDecisionNode,
            ChallengeFollowUpGenNode challengeFollowUpGenNode,
            DeepDiveFollowUpGenNode deepDiveFollowUpGenNode) {
        this.askQuestionNode = askQuestionNode;
        this.basicFollowUpGenNode = basicFollowUpGenNode;
        this.roundRouter = roundRouter;
        this.comprehensiveEvaluationNode = comprehensiveEvaluationNode;
        this.followUpDecisionNode = followUpDecisionNode;
        this.challengeFollowUpGenNode = challengeFollowUpGenNode;
        this.deepDiveFollowUpGenNode = deepDiveFollowUpGenNode;
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
        String askQuestion = prefix + NodeNames.ASK_QUESTION;
        String followUpDecision = prefix + NodeNames.FOLLOW_UP_DECISION;
        String generateFollowUp = prefix + NodeNames.GENERATE_FOLLOW_UP;

        // Omni多模态综合评估节点
        String comprehensiveEval = prefix + NodeNames.COMPREHENSIVE_EVALUATION;

        // 条件分支节点
        String generateChallenge = prefix + NodeNames.GENERATE_CHALLENGE;
        String generateDeepDive = prefix + NodeNames.GENERATE_DEEP_DIVE;

        // 返回未编译的 StateGraph，由主图统一编译
        return new StateGraph<>(InterviewState.SCHEMA, InterviewState::new)
                // ========== 添加节点 ==========
                .addNode(askQuestion, askQuestionNode::execute)

                // Omni多模态综合评估（单节点替代并行三分支）
                .addNode(comprehensiveEval, comprehensiveEvaluationNode::execute)

                // 条件分支节点
                .addNode(followUpDecision, followUpDecisionNode::execute)
                .addNode(generateFollowUp, basicFollowUpGenNode::execute)
                .addNode(generateChallenge, challengeFollowUpGenNode::execute)
                .addNode(generateDeepDive, deepDiveFollowUpGenNode::execute)

                // ========== 定义边 ==========
                // 直接从 START 进入 askQuestion
                .addEdge(START, askQuestion)

                // 恢复执行后直接进入综合评估
                .addEdge(askQuestion, comprehensiveEval)

                // 综合评估后进入追问决策
                .addEdge(comprehensiveEval, followUpDecision)

                // ========== 条件路由：追问决策 + 轮次完成 ==========
                .addConditionalEdges(
                        followUpDecision,
                        state -> CompletableFuture.completedFuture(roundRouter.route(state)),
                        Map.of(
                                RouteDecision.FOLLOW_UP.getValue(), generateFollowUp,
                                RouteDecision.DEEP_DIVE.getValue(), generateDeepDive,
                                RouteDecision.CHALLENGE_MODE.getValue(), generateChallenge,
                                RouteDecision.NEXT_QUESTION.getValue(), askQuestion,   // 内部消化：回到提问
                                RouteDecision.ROUND_COMPLETE.getValue(), END           // 轮次结束：子图退出
                        )
                )

                // 所有追问分支最终回到 askQuestion 形成循环
                .addEdge(generateFollowUp, askQuestion)
                .addEdge(generateChallenge, askQuestion)
                .addEdge(generateDeepDive, askQuestion);
        // 注意：不在这里 compile()，由主图统一编译
    }
}
