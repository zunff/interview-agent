package com.zunff.interview.agent.nodes;

import com.zunff.interview.service.interview.ReportGeneratorService;
import com.zunff.interview.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 问题分析节点
 * 为每道题生成考察意图分析和标准答案
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionAnalysisNode {

    private final ReportGeneratorService reportGeneratorService;

    /**
     * 执行问题分析
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始问题分析，当前问题: {}", state.currentQuestion());

        String question = state.currentQuestion();
        String questionType = state.questionType();
        String jobInfo = state.jobInfo();
        String resume = state.resume();

        // 生成问题分析
        return reportGeneratorService.generateQuestionAnalysis(question, questionType, jobInfo, resume)
                .thenApply(result -> {
                    Map<String, Object> updates = new HashMap<>();

                    // 将分析结果添加到状态（用于最终报告）
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> analyses = (List<Map<String, Object>>)
                            state.data().getOrDefault("questionAnalyses", new ArrayList<>());

                    List<Map<String, Object>> newAnalyses = new ArrayList<>(analyses);
                    Map<String, Object> analysis = new HashMap<>();
                    analysis.put("question", question);
                    analysis.put("questionType", questionType);
                    analysis.put("interviewIntent", result.interviewIntent());
                    analysis.put("standardAnswer", result.standardAnswer());
                    newAnalyses.add(analysis);

                    updates.put("questionAnalyses", newAnalyses);

                    log.info("问题分析完成: 考察意图={}", result.interviewIntent());

                    return updates;
                });
    }
}