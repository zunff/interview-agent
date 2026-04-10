package com.zunff.interview.agent.graph;

import com.zunff.interview.agent.nodes.InitInterviewNode;
import com.zunff.interview.agent.nodes.JobAnalysisNode;
import com.zunff.interview.agent.nodes.ReportGeneratorNode;
import com.zunff.interview.agent.nodes.RoundTransitionNode;
import com.zunff.interview.agent.router.RoundTransitionRouter;
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
 * START -> init -> jobAnalysis -> technicalRound(子图) -> roundTransition -> businessRound(子图) -> generateReport -> END
 *
 * 注意：子图会在waitForAnswer节点暂停并返回END
 * 主图需要正确处理这种情况
 */
@Slf4j
@Configuration
public class InterviewAgentGraph {

    private final InitInterviewNode initInterviewNode;
    private final JobAnalysisNode jobAnalysisNode;
    private final ReportGeneratorNode reportGeneratorNode;
    private final RoundTransitionNode roundTransitionNode;
    private final RoundTransitionRouter roundTransitionRouter;
    private final InterviewRoundGraph interviewRoundGraph;
    private final GraphConfigProperties graphConfig;

    public InterviewAgentGraph(
            InitInterviewNode initInterviewNode,
            JobAnalysisNode jobAnalysisNode,
            ReportGeneratorNode reportGeneratorNode,
            RoundTransitionNode roundTransitionNode,
            RoundTransitionRouter roundTransitionRouter,
            InterviewRoundGraph interviewRoundGraph,
            GraphConfigProperties graphConfig) {
        this.initInterviewNode = initInterviewNode;
        this.jobAnalysisNode = jobAnalysisNode;
        this.reportGeneratorNode = reportGeneratorNode;
        this.roundTransitionNode = roundTransitionNode;
        this.roundTransitionRouter = roundTransitionRouter;
        this.interviewRoundGraph = interviewRoundGraph;
        this.graphConfig = graphConfig;
    }

    /**
     * 创建面试 Agent 主图
     */
    @Bean
    public CompiledGraph<InterviewState> interviewAgent() throws GraphStateException {
        log.info("初始化面试 Agent 主图（子图架构 + 岗位分析）");

        return new StateGraph<>(InterviewState.SCHEMA, InterviewState::new)
                // ========== 添加节点 ==========
                .addNode(NodeNames.INIT, initInterviewNode::execute)
                .addNode(NodeNames.JOB_ANALYSIS, jobAnalysisNode::execute)
                .addNode(NodeNames.TECHNICAL_ROUND, interviewRoundGraph.createGraph(InterviewRound.TECHNICAL))
                .addNode(NodeNames.ROUND_TRANSITION, roundTransitionNode::execute)
                .addNode(NodeNames.BUSINESS_ROUND, interviewRoundGraph.createGraph(InterviewRound.BUSINESS))
                .addNode(NodeNames.GENERATE_REPORT, reportGeneratorNode::execute)

                // ========== 定义边 ==========
                .addEdge(START, NodeNames.INIT)
                .addEdge(NodeNames.INIT, NodeNames.JOB_ANALYSIS)
                .addEdge(NodeNames.JOB_ANALYSIS, NodeNames.TECHNICAL_ROUND)

                // ========== 条件路由：检查技术轮子图状态 ==========
                // 如果正在等待答案，返回 END 暂停主图
                // 否则继续执行轮次切换
                .addConditionalEdges(
                        NodeNames.TECHNICAL_ROUND,
                        state -> CompletableFuture.completedFuture(checkTechnicalRoundStatus(state)),
                        Map.of(
                                "waiting", END,  // 等待答案时暂停主图
                                "continue", NodeNames.ROUND_TRANSITION  // 继续执行轮次切换
                        )
                )

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

                // ========== 条件路由：检查业务轮子图状态 ==========
                .addConditionalEdges(
                        NodeNames.BUSINESS_ROUND,
                        state -> CompletableFuture.completedFuture(checkBusinessRoundStatus(state)),
                        Map.of(
                                "waiting", END,  // 等待答案时暂停主图
                                "continue", NodeNames.GENERATE_REPORT  // 业务轮完成后生成报告
                        )
                )

                .addEdge(NodeNames.GENERATE_REPORT, END)
                .compile(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())
                        .recursionLimit(graphConfig.getMainRecursionLimit())
                        .build());
    }

    /**
     * 检查技术轮子图状态
     */
    private String checkTechnicalRoundStatus(InterviewState state) {
        boolean waiting = state.isWaitingForAnswer();
        log.debug("[技术轮] 检查等待状态: waiting={}", waiting);
        return waiting ? "waiting" : "continue";
    }

    /**
     * 检查业务轮子图状态
     */
    private String checkBusinessRoundStatus(InterviewState state) {
        boolean waiting = state.isWaitingForAnswer();
        log.debug("[业务轮] 检查等待状态: waiting={}", waiting);
        return waiting ? "waiting" : "continue";
    }
}
