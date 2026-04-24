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
 *
 * 架构：
 * START → questionPlanning → [TechBasicGen/ProjectGen/BusinessGen/SoftSkillGen 并行]
 *                           → aggregateResults → END
 *
 * 规划节点在 4 个生成节点之前运行，确保各类题目覆盖不同维度，避免重叠。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchQuestionSubgraph {

    private final QuestionPlanningNode questionPlanningNode;
    private final TechBasicGenNode techBasicGenNode;
    private final ProjectGenNode projectGenNode;
    private final BusinessGenNode businessGenNode;
    private final SoftSkillGenNode softSkillGenNode;
    private final AggregateResultsNode aggregateResultsNode;

    /**
     * 创建并编译子图
     */
    public CompiledGraph<BatchQuestionGenState> createCompiledGraph() throws GraphStateException {
        log.info("创建批量题目生成子图（含规划节点）");

        return new StateGraph<>(BatchQuestionGenState.SCHEMA, BatchQuestionGenState::new)
                // ========== 规划节点（先运行，协调话题分配） ==========
                .addNode(QuestionGenNodeNames.GEN_PLANNING, questionPlanningNode::execute)

                // ========== 4个生成节点 ==========
                .addNode(QuestionGenNodeNames.GEN_TECH_BASIC, techBasicGenNode::execute)
                .addNode(QuestionGenNodeNames.GEN_PROJECT, projectGenNode::execute)
                .addNode(QuestionGenNodeNames.GEN_BUSINESS, businessGenNode::execute)
                .addNode(QuestionGenNodeNames.GEN_SOFT_SKILL, softSkillGenNode::execute)

                // ========== 聚合节点 ==========
                .addNode(QuestionGenNodeNames.AGGREGATE_RESULTS, aggregateResultsNode::execute)

                // ========== 边定义 ==========
                // START → 规划节点（串行，规划完成后才开启并行生成）
                .addEdge(START, QuestionGenNodeNames.GEN_PLANNING)

                // 规划节点 → 4个生成节点并行
                .addEdge(QuestionGenNodeNames.GEN_PLANNING, QuestionGenNodeNames.GEN_TECH_BASIC)
                .addEdge(QuestionGenNodeNames.GEN_PLANNING, QuestionGenNodeNames.GEN_PROJECT)
                .addEdge(QuestionGenNodeNames.GEN_PLANNING, QuestionGenNodeNames.GEN_BUSINESS)
                .addEdge(QuestionGenNodeNames.GEN_PLANNING, QuestionGenNodeNames.GEN_SOFT_SKILL)

                // 汇聚到聚合节点
                .addEdge(QuestionGenNodeNames.GEN_TECH_BASIC, QuestionGenNodeNames.AGGREGATE_RESULTS)
                .addEdge(QuestionGenNodeNames.GEN_PROJECT, QuestionGenNodeNames.AGGREGATE_RESULTS)
                .addEdge(QuestionGenNodeNames.GEN_BUSINESS, QuestionGenNodeNames.AGGREGATE_RESULTS)
                .addEdge(QuestionGenNodeNames.GEN_SOFT_SKILL, QuestionGenNodeNames.AGGREGATE_RESULTS)

                // 聚合
                .addEdge(QuestionGenNodeNames.AGGREGATE_RESULTS, END)
                // 编译子图
                .compile();
    }
}
