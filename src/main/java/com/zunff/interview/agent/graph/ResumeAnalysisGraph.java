package com.zunff.interview.agent.graph;

import com.zunff.interview.agent.nodes.resume.*;
import com.zunff.interview.agent.state.ResumeState;
import com.zunff.interview.config.ResumeAnalysisConfig;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

@Slf4j
@Configuration
public class ResumeAnalysisGraph {

    private static final String UPLOAD = "upload";
    private static final String PARSE = "parse";
    private static final String COMPANY = "company";
    private static final String SKILL = "skill";
    private static final String EXP = "exp";
    private static final String BG = "bg";
    private static final String POTENTIAL = "potential";
    private static final String REPORT = "report";
    private static final String PERSIST = "persist";

    private final UploadGuardNode uploadGuardNode;
    private final ParseResumeNode parseResumeNode;
    private final CompanyResearchNode companyResearchNode;
    private final SkillDimensionNode skillDimensionNode;
    private final ExpDimensionNode expDimensionNode;
    private final BgDimensionNode bgDimensionNode;
    private final PotentialDimensionNode potentialDimensionNode;
    private final ReportGenNode reportGenNode;
    private final PersistAnalysisNode persistAnalysisNode;
    private final ResumeAnalysisConfig config;

    public ResumeAnalysisGraph(UploadGuardNode uploadGuardNode,
                                ParseResumeNode parseResumeNode,
                                CompanyResearchNode companyResearchNode,
                                SkillDimensionNode skillDimensionNode,
                                ExpDimensionNode expDimensionNode,
                                BgDimensionNode bgDimensionNode,
                                PotentialDimensionNode potentialDimensionNode,
                                ReportGenNode reportGenNode,
                                PersistAnalysisNode persistAnalysisNode,
                                ResumeAnalysisConfig config) {
        this.uploadGuardNode = uploadGuardNode;
        this.parseResumeNode = parseResumeNode;
        this.companyResearchNode = companyResearchNode;
        this.skillDimensionNode = skillDimensionNode;
        this.expDimensionNode = expDimensionNode;
        this.bgDimensionNode = bgDimensionNode;
        this.potentialDimensionNode = potentialDimensionNode;
        this.reportGenNode = reportGenNode;
        this.persistAnalysisNode = persistAnalysisNode;
        this.config = config;
    }

    @Bean
    public CompiledGraph<ResumeState> resumeAnalysisCompiledGraph() {
        log.info("初始化简历分析图（完整流水线）");

        try {
            StateGraph<ResumeState> graph = new StateGraph<>(ResumeState.SCHEMA, ResumeState::new)
                // 节点
                .addNode(UPLOAD, uploadGuardNode::execute)
                .addNode(PARSE, parseResumeNode::execute)
                .addNode(COMPANY, companyResearchNode::execute)
                .addNode(SKILL, skillDimensionNode::execute)
                .addNode(EXP, expDimensionNode::execute)
                .addNode(BG, bgDimensionNode::execute)
                .addNode(POTENTIAL, potentialDimensionNode::execute)
                .addNode(REPORT, reportGenNode::execute)
                .addNode(PERSIST, persistAnalysisNode::execute)

                // 线性：upload → parse → company
                .addEdge(START, UPLOAD)
                .addEdge(UPLOAD, PARSE)
                .addEdge(PARSE, COMPANY)

                // 扇出：company → 4 个并行评估节点
                .addEdge(COMPANY, SKILL)
                .addEdge(COMPANY, EXP)
                .addEdge(COMPANY, BG)
                .addEdge(COMPANY, POTENTIAL)

                // 扇入：4 个评估节点 → report（生成报告 + 雷达图 JSON）
                .addEdge(SKILL, REPORT)
                .addEdge(EXP, REPORT)
                .addEdge(BG, REPORT)
                .addEdge(POTENTIAL, REPORT)

                // 线性：report → persist → END
                .addEdge(REPORT, PERSIST)
                .addEdge(PERSIST, END);

        return graph.compile(CompileConfig.builder()
                .recursionLimit(config.getRecursionLimit())
                .build());
        } catch (Exception e) {
            throw new IllegalStateException("简历分析图初始化失败", e);
        }
    }
}
