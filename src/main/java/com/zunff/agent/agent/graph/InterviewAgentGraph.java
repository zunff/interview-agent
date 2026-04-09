package com.zunff.agent.agent.graph;

import com.zunff.agent.agent.nodes.InitInterviewNode;
import com.zunff.agent.agent.nodes.ReportGeneratorNode;
import com.zunff.agent.agent.nodes.RoundTransitionNode;
import com.zunff.agent.agent.router.RoundTransitionRouter;
import com.zunff.agent.constant.InterviewRound;
import com.zunff.agent.constant.NodeNames;
import com.zunff.agent.constant.RouteDecision;
import com.zunff.agent.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 面试 Agent 主图配置
 *
 * 架构:
 * START → init → technicalRound(子图) → roundTransition → businessRound(子图) → generateReport → END
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class InterviewAgentGraph {

    private final InitInterviewNode initInterviewNode;
    private final ReportGeneratorNode reportGeneratorNode;
    private final RoundTransitionNode roundTransitionNode;
    private final RoundTransitionRouter roundTransitionRouter;
    private final InterviewRoundGraph interviewRoundGraph;

    /**
     * 创建面试 Agent 主图
     */
    @Bean
    public CompiledGraph<InterviewState> interviewAgent() throws GraphStateException {
        log.info("初始化面试 Agent 主图（子图架构）");

        return new StateGraph<>(InterviewState.SCHEMA, InterviewState::new)
                // ========== 添加节点 ==========
                .addNode(NodeNames.INIT, initInterviewNode::execute)
                .addNode(NodeNames.TECHNICAL_ROUND, interviewRoundGraph.createGraph(InterviewRound.TECHNICAL))
                .addNode(NodeNames.ROUND_TRANSITION, roundTransitionNode::execute)
                .addNode(NodeNames.BUSINESS_ROUND, interviewRoundGraph.createGraph(InterviewRound.BUSINESS))
                .addNode(NodeNames.GENERATE_REPORT, reportGeneratorNode::execute)

                // ========== 定义边 ==========
                .addEdge(START, NodeNames.INIT)
                .addEdge(NodeNames.INIT, NodeNames.TECHNICAL_ROUND)
                .addEdge(NodeNames.TECHNICAL_ROUND, NodeNames.ROUND_TRANSITION)

                // ========== 轮次路由 ==========
                .addConditionalEdges(
                        NodeNames.ROUND_TRANSITION,
                        state -> CompletableFuture.completedFuture(roundTransitionRouter.route(state)),
                        Map.of(
                                RouteDecision.TECHNICAL_TO_BUSINESS.getValue(), NodeNames.BUSINESS_ROUND,
                                RouteDecision.BUSINESS_DONE.getValue(), NodeNames.GENERATE_REPORT,
                                RouteDecision.CONTINUE.getValue(), NodeNames.TECHNICAL_ROUND,
                                RouteDecision.EARLY_END.getValue(), NodeNames.GENERATE_REPORT
                        )
                )
                .addEdge(NodeNames.BUSINESS_ROUND, NodeNames.GENERATE_REPORT)
                .addEdge(NodeNames.GENERATE_REPORT, END)

                // ========== 编译配置 ==========
                .compile(CompileConfig.builder()
                        .interruptBefore(
                                InterviewRoundGraph.getWaitForAnswerNodeName(InterviewRound.TECHNICAL),
                                InterviewRoundGraph.getWaitForAnswerNodeName(InterviewRound.BUSINESS)
                        )
                        .build());
    }
}