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
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 面试 Agent 主图配置
 *
 * 架构:
 * START -> init -> jobAnalysis -> technicalRound(子图) -> roundTransition -> businessRound(子图) -> generateReport -> END
 *
 * 子图使用 addSubGraph() 合并到主图，状态共享
 * interruptsAfter 在主图编译时设置，子图节点合并后可直接暂停
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
     * 使用 addSubGraph() 合并子图，状态共享
     */
    @Bean
    public CompiledGraph<InterviewState> interviewAgent() throws GraphStateException {
        log.info("初始化面试 Agent 主图（子图架构 + 岗位分析）");

        // 获取子图的 StateGraph（未编译）
        StateGraph<InterviewState> technicalSubgraph = interviewRoundGraph.createGraph(InterviewRound.TECHNICAL);
        StateGraph<InterviewState> businessSubgraph = interviewRoundGraph.createGraph(InterviewRound.BUSINESS);

        // 子图内部的 askQuestion 节点名称（用于 interruptsAfter）
        String techAskQuestion = NodeNames.TECH_PREFIX + NodeNames.ASK_QUESTION;
        String bizAskQuestion = NodeNames.BIZ_PREFIX + NodeNames.ASK_QUESTION;

        return new StateGraph<>(InterviewState.SCHEMA, InterviewState::new)
                // ========== 添加节点 ==========
                .addNode(NodeNames.INIT, initInterviewNode::execute)
                .addNode(NodeNames.JOB_ANALYSIS, jobAnalysisNode::execute)

                // 使用 addSubgraph() 合并子图（状态共享）
                .addNode(NodeNames.TECHNICAL_ROUND, technicalSubgraph)
                .addNode(NodeNames.BUSINESS_ROUND, businessSubgraph)

                .addNode(NodeNames.ROUND_TRANSITION, roundTransitionNode::execute)
                .addNode(NodeNames.GENERATE_REPORT, reportGeneratorNode::execute)

                // ========== 定义边 ==========
                .addEdge(START, NodeNames.INIT)
                .addEdge(NodeNames.INIT, NodeNames.JOB_ANALYSIS)
                .addEdge(NodeNames.JOB_ANALYSIS, NodeNames.TECHNICAL_ROUND)

                // 技术轮完成后进入轮次切换
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

                // 业务轮完成后生成报告
                .addEdge(NodeNames.BUSINESS_ROUND, NodeNames.GENERATE_REPORT)

                .addEdge(NodeNames.GENERATE_REPORT, END)

                // 主图统一编译，设置 checkpointSaver 和 interruptsAfter
                .compile(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())  // 只有主图有 MemorySaver
                        .recursionLimit(graphConfig.getMainRecursionLimit())
                        // 在主图编译时设置 interruptsAfter（子图节点格式：subgraphId-nodeId）
                        .interruptsAfter(Set.of(
                                NodeNames.TECHNICAL_ROUND + "-" + techAskQuestion,
                                NodeNames.BUSINESS_ROUND + "-" + bizAskQuestion
                        ))
                        .build());
    }

}