package com.zunff.interview.agent.graph;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.agent.names.NodeNames;
import com.zunff.interview.agent.nodes.main.*;
import com.zunff.interview.agent.state.BatchQuestionGenState;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.config.GraphConfigProperties;
import com.zunff.interview.constant.InterviewRound;
import com.zunff.interview.model.dto.GeneratedQuestion;
import com.zunff.interview.model.dto.JobAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 面试 Agent 主图配置
 *
 * 架构:
 * START → init → jobAnalysis/selfIntro(并行) → profileAnalysis → batchQuestionGenerator
 * → technicalRound → roundTransition → businessRound → generateReport → END
 */
@Slf4j
@Configuration
public class InterviewAgentGraph {

    private final InitInterviewNode initInterviewNode;
    private final JobAnalysisNode jobAnalysisNode;
    private final SelfIntroNode selfIntroNode;
    private final ProfileAnalysisNode profileAnalysisNode;
    private final BatchQuestionSubgraph batchQuestionSubgraph;
    private final ReportGeneratorNode reportGeneratorNode;
    private final RoundTransitionNode roundTransitionNode;
    private final InterviewRoundSubGraph interviewRoundSubGraph;
    private final GraphConfigProperties graphConfig;
    private final ExecutorService virtualThreadExecutor;

    // 预编译的批量题目生成子图
    private CompiledGraph<BatchQuestionGenState> compiledBatchQuestionGraph;

    public InterviewAgentGraph(
            InitInterviewNode initInterviewNode,
            JobAnalysisNode jobAnalysisNode,
            SelfIntroNode selfIntroNode,
            ProfileAnalysisNode profileAnalysisNode,
            BatchQuestionSubgraph batchQuestionSubgraph,
            ReportGeneratorNode reportGeneratorNode,
            RoundTransitionNode roundTransitionNode,
            InterviewRoundSubGraph interviewRoundSubGraph,
            GraphConfigProperties graphConfig,
            ExecutorService virtualThreadExecutor) {
        this.initInterviewNode = initInterviewNode;
        this.jobAnalysisNode = jobAnalysisNode;
        this.selfIntroNode = selfIntroNode;
        this.profileAnalysisNode = profileAnalysisNode;
        this.batchQuestionSubgraph = batchQuestionSubgraph;
        this.reportGeneratorNode = reportGeneratorNode;
        this.roundTransitionNode = roundTransitionNode;
        this.interviewRoundSubGraph = interviewRoundSubGraph;
        this.graphConfig = graphConfig;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    /**
     * 创建面试 Agent 主图
     */
    @Bean
    public CompiledGraph<InterviewState> interviewAgent() throws GraphStateException {
        log.info("初始化面试 Agent 主图");

        // 预编译批量题目生成子图
        compiledBatchQuestionGraph = batchQuestionSubgraph.createCompiledGraph();

        // 获取子图的 StateGraph（未编译）
        StateGraph<InterviewState> technicalSubgraph = interviewRoundSubGraph.createGraph(InterviewRound.TECHNICAL);
        StateGraph<InterviewState> businessSubgraph = interviewRoundSubGraph.createGraph(InterviewRound.BUSINESS);

        // 子图内部的 askQuestion 节点名称（用于 interruptsAfter）
        String techAskQuestion = NodeNames.TECH_PREFIX + NodeNames.ASK_QUESTION;
        String bizAskQuestion = NodeNames.BIZ_PREFIX + NodeNames.ASK_QUESTION;

        return new StateGraph<>(InterviewState.SCHEMA, InterviewState::new)
                // ========== 添加节点 ==========
                .addNode(NodeNames.INIT, initInterviewNode::execute)
                .addNode(NodeNames.JOB_ANALYSIS, jobAnalysisNode::execute)
                .addNode(NodeNames.SELF_INTRO, selfIntroNode::execute)
                .addNode(NodeNames.PROFILE_ANALYSIS, profileAnalysisNode::execute)
                .addNode(NodeNames.BATCH_QUESTION_GENERATOR, this::callBatchQuestionSubgraphAsync)

                .addNode(NodeNames.TECHNICAL_ROUND, technicalSubgraph)
                .addNode(NodeNames.ROUND_TRANSITION, roundTransitionNode::execute)
                .addNode(NodeNames.BUSINESS_ROUND, businessSubgraph)

                .addNode(NodeNames.GENERATE_REPORT, reportGeneratorNode::execute)

                // ========== 定义边 ==========
                .addEdge(START, NodeNames.INIT)
                // fan out 并行执行 岗位分析、自我介绍
                .addEdge(NodeNames.INIT, NodeNames.JOB_ANALYSIS)
                .addEdge(NodeNames.INIT, NodeNames.SELF_INTRO)
                // fan in 在执行人物画像分析前 等待自我介绍完成
                .addEdge(NodeNames.JOB_ANALYSIS, NodeNames.PROFILE_ANALYSIS)
                .addEdge(NodeNames.SELF_INTRO, NodeNames.PROFILE_ANALYSIS)

                // 批量生成题目后进入技术轮
                .addEdge(NodeNames.PROFILE_ANALYSIS, NodeNames.BATCH_QUESTION_GENERATOR)
                .addEdge(NodeNames.BATCH_QUESTION_GENERATOR, NodeNames.TECHNICAL_ROUND)

                // 技术轮 → 轮次切换 → 业务轮 → 报告
                .addEdge(NodeNames.TECHNICAL_ROUND, NodeNames.ROUND_TRANSITION)
                .addEdge(NodeNames.ROUND_TRANSITION, NodeNames.BUSINESS_ROUND)
                .addEdge(NodeNames.BUSINESS_ROUND, NodeNames.GENERATE_REPORT)
                .addEdge(NodeNames.GENERATE_REPORT, END)

                // 主图统一编译
                .compile(CompileConfig.builder()
                        .checkpointSaver(new MemorySaver())
                        .recursionLimit(graphConfig.getMainRecursionLimit())
                        .interruptsBefore(Set.of(NodeNames.PROFILE_ANALYSIS)) // 自我介绍需要等待回答
                        .interruptsAfter(Set.of(
                                NodeNames.TECHNICAL_ROUND + "-" + techAskQuestion,
                                NodeNames.BUSINESS_ROUND + "-" + bizAskQuestion
                        ))
                        .build());
    }

    // ================= 主图节点逻辑 =================

    /**
     * 异步调用批量题目生成子图（官方标准写法）
     * 使用 stream().reduceAsync() 获取 CompletableFuture
     */
    private CompletableFuture<Map<String, Object>> callBatchQuestionSubgraphAsync(InterviewState mainState) {
        log.info("主图调用批量题目生成子图");

        // 1. 主图状态 → 子图状态映射
        JobAnalysisResult jobAnalysis = mainState.jobAnalysisResult();

        Map<String, Object> subInputMap = new HashMap<>();
        subInputMap.put(BatchQuestionGenState.CANDIDATE_PROFILE, mainState.candidateProfile());
        subInputMap.put(BatchQuestionGenState.JOB_CONTEXT, jobAnalysis != null ? jobAnalysis.generateRagQuery() : "");
        subInputMap.put(BatchQuestionGenState.SESSION_ID, mainState.sessionId());
        subInputMap.put(BatchQuestionGenState.KNOWLEDGE_COMPANY, Optional.ofNullable(mainState.knowledgeCompany()).orElse(""));
        subInputMap.put(BatchQuestionGenState.KNOWLEDGE_JOB_POSITION, Optional.ofNullable(mainState.knowledgeJobPosition()).orElse(""));
        subInputMap.put(BatchQuestionGenState.TECHNICAL_BASIC_COUNT, jobAnalysis != null ? jobAnalysis.getTechnicalBasicCount() : mainState.maxTechnicalQuestions() / 2);
        subInputMap.put(BatchQuestionGenState.PROJECT_COUNT, jobAnalysis != null ? jobAnalysis.getProjectCount() : mainState.maxTechnicalQuestions() - mainState.maxTechnicalQuestions() / 2);
        subInputMap.put(BatchQuestionGenState.BUSINESS_COUNT, jobAnalysis != null ? jobAnalysis.getBusinessCount() : mainState.maxBusinessQuestions() / 2);
        subInputMap.put(BatchQuestionGenState.SOFT_SKILL_COUNT, jobAnalysis != null ? jobAnalysis.getSoftSkillCount() : mainState.maxBusinessQuestions() - mainState.maxBusinessQuestions() / 2);
        subInputMap.put(BatchQuestionGenState.LEVEL_MATCH_RESULT, mainState.levelMatchResult());

        // 2. 异步调用子图
        RunnableConfig config = RunnableConfig.builder()
                .threadId(mainState.sessionId())
                .addParallelNodeExecutor(StateGraph.START, virtualThreadExecutor)
                .build();

        // 3. 用 AtomicReference 捕获最后一个状态
        AtomicReference<NodeOutput<BatchQuestionGenState>> lastStateRef = new AtomicReference<>();
        return compiledBatchQuestionGraph.stream(subInputMap, config)
                // 使用 reduceAsync 获取最终状态
                .forEachAsync(lastStateRef::set)
                .thenApply(voidResult -> {
                    NodeOutput<BatchQuestionGenState> nodeOutput = lastStateRef.get();
                    // 3. 子图结果 → 主图状态更新
                    if (nodeOutput == null) {
                        log.error("子图执行返回空，触发主图降级和熔断计数");
                        Map<String, Object> updates = generateFallbackUpdates();
                        // 熔断逻辑：递增失败次数
                        CircuitBreakerHelper.handleFailure(mainState, updates,
                                new RuntimeException("Batch question generation failed: null output"));
                        return updates;
                    }

                    BatchQuestionGenState subState = nodeOutput.state();

                    Map<String, Object> updates = new HashMap<>();
                    updates.put(InterviewState.TECHNICAL_QUESTIONS_QUEUE, subState.getTechnicalQuestionsQueue());
                    updates.put(InterviewState.BUSINESS_QUESTIONS_QUEUE, subState.getBusinessQuestionsQueue());
                    updates.put(InterviewState.CURRENT_TECHNICAL_INDEX, subState.getCurrentTechnicalIndex());
                    updates.put(InterviewState.CURRENT_BUSINESS_INDEX, subState.getCurrentBusinessIndex());

                    // 熔断逻辑：根据 fallback 标志决定是否记录失败
                    if (subState.getFallback()) {
                        log.warn("批量题目生成触发降级，记录失败");
                        CircuitBreakerHelper.handleFailure(mainState, updates, new RuntimeException("Batch question generation fallback triggered"));
                    } else {
                        log.info("批量题目生成成功，重置失败计数");
                        CircuitBreakerHelper.recordSuccess(updates);
                    }

                    log.info("子图执行完成，技术轮 {} 题，业务轮 {} 题", subState.getTechnicalQuestionsQueue().size(), subState.getBusinessQuestionsQueue().size());

                    return updates;
                })
                .exceptionally(e -> {
                    log.error("子图执行异常", e);
                    Map<String, Object> updates = generateFallbackUpdates();
                    // 熔断逻辑：递增失败次数
                    CircuitBreakerHelper.handleFailure(mainState, updates, new Exception(e));
                    return updates;
                });
    }

    /**
     * 双重降级：生成默认题目
     */
    private Map<String, Object> generateFallbackUpdates() {
        return Map.of(
                InterviewState.TECHNICAL_QUESTIONS_QUEUE, generateDefaultTechnicalQuestions(),
                InterviewState.BUSINESS_QUESTIONS_QUEUE, generateDefaultBusinessQuestions(),
                InterviewState.CURRENT_TECHNICAL_INDEX, 0,
                InterviewState.CURRENT_BUSINESS_INDEX, 0
        );
    }

    /**
     * 生成默认技术轮题目（降级用）
     */
    private List<GeneratedQuestion> generateDefaultTechnicalQuestions() {
        List<GeneratedQuestion> questions = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            questions.add(GeneratedQuestion.builder()
                    .question("请简单介绍一下你的技术背景和项目经验。")
                    .questionType(i < 3 ? "技术基础" : "项目经验")
                    .expectedKeywords(List.of())
                    .difficulty("medium")
                    .reason("Fallback question due to generation failure")
                    .questionIndex(i + 1)
                    .build());
        }
        return questions;
    }

    /**
     * 生成默认业务轮题目（降级用）
     */
    private List<GeneratedQuestion> generateDefaultBusinessQuestions() {
        List<GeneratedQuestion> questions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            questions.add(GeneratedQuestion.builder()
                    .question("请简单介绍一下你的技术背景和项目经验。")
                    .questionType(i < 2 ? "业务场景" : "沟通协作")
                    .expectedKeywords(List.of())
                    .difficulty("medium")
                    .reason("Fallback question due to generation failure")
                    .questionIndex(i + 1)
                    .build());
        }
        return questions;
    }
}