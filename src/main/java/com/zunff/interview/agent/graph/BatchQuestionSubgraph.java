package com.zunff.interview.agent.graph;

import com.zunff.interview.agent.names.QuestionGenNodeNames;
import com.zunff.interview.agent.nodes.question.gen.*;
import com.zunff.interview.agent.state.BatchQuestionGenState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

/**
 * 批量题目生成子图
 * 使用独立的 Node 类实现，清晰易维护
 *
 * 架构：
 * START → [4个生成节点并行] → aggregateResults → handleSideEffects → END
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchQuestionSubgraph {

    private final TechBasicGenNode techBasicGenNode;
    private final ProjectGenNode projectGenNode;
    private final BusinessGenNode businessGenNode;
    private final SoftSkillGenNode softSkillGenNode;
    private final AggregateResultsNode aggregateResultsNode;

    /**
     * 创建并编译子图
     */
    public CompiledGraph<BatchQuestionGenState> createCompiledGraph() throws GraphStateException {
        log.info("创建批量题目生成子图（CompiledGraph 模式 + 独立 Node 类）");

        return new StateGraph<>(BatchQuestionGenState.SCHEMA, BatchQuestionGenState::new)
                // ========== 4个生成节点 ==========
                .addNode(QuestionGenNodeNames.GEN_TECH_BASIC, techBasicGenNode::execute)
                .addNode(QuestionGenNodeNames.GEN_PROJECT, projectGenNode::execute)
                .addNode(QuestionGenNodeNames.GEN_BUSINESS, businessGenNode::execute)
                .addNode(QuestionGenNodeNames.GEN_SOFT_SKILL, softSkillGenNode::execute)

                // ========== 聚合和副作用节点 ==========
                .addNode(QuestionGenNodeNames.AGGREGATE_RESULTS, aggregateResultsNode::execute)

                // ========== 边定义 ==========
                // 4个生成节点并行执行（从 START 到各节点）
                .addEdge(START, QuestionGenNodeNames.GEN_TECH_BASIC)
                .addEdge(START, QuestionGenNodeNames.GEN_PROJECT)
                .addEdge(START, QuestionGenNodeNames.GEN_BUSINESS)
                .addEdge(START, QuestionGenNodeNames.GEN_SOFT_SKILL)

                // 汇聚到聚合节点
                .addEdge(QuestionGenNodeNames.GEN_TECH_BASIC, QuestionGenNodeNames.AGGREGATE_RESULTS)
                .addEdge(QuestionGenNodeNames.GEN_PROJECT, QuestionGenNodeNames.AGGREGATE_RESULTS)
                .addEdge(QuestionGenNodeNames.GEN_BUSINESS, QuestionGenNodeNames.AGGREGATE_RESULTS)
                .addEdge(QuestionGenNodeNames.GEN_SOFT_SKILL, QuestionGenNodeNames.AGGREGATE_RESULTS)

                // 聚合后处理副作用
                .addEdge(QuestionGenNodeNames.AGGREGATE_RESULTS, END)
                // 编译子图
                .compile();
    }
}
