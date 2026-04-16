package com.zunff.interview.agent.nodes.question.gen;

import cn.hutool.json.JSONUtil;
import com.zunff.interview.model.dto.GeneratedQuestion;
import com.zunff.interview.agent.state.BatchQuestionGenState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 结果聚合节点
 * 负责：
 * 1. 聚合4类题目
 * 2. 重新编号
 * 3. 降级处理
 */
@Slf4j
@Component
public class AggregateResultsNode {

    public CompletableFuture<Map<String, Object>> execute(BatchQuestionGenState state) {
        log.info("开始聚合题目生成结果");

        List<GeneratedQuestion> technicalBasic = state.getTechnicalBasicQuestions();
        List<GeneratedQuestion> project = state.getProjectQuestions();
        List<GeneratedQuestion> business = state.getBusinessQuestions();
        List<GeneratedQuestion> softSkill = state.getSoftSkillQuestions();

        log.info("生成结果：技术基础 {} 题，项目经验 {} 题，业务理解 {} 题，软技能 {} 题",
                technicalBasic.size(), project.size(), business.size(), softSkill.size());

        // 检查是否需要降级（核心题目全部失败）
        boolean needsFallback = technicalBasic.isEmpty() && project.isEmpty();

        List<GeneratedQuestion> technicalQueue;
        List<GeneratedQuestion> businessQueue;

        if (needsFallback) {
            log.warn("所有技术题生成失败，使用默认题目");
            technicalQueue = generateDefaultTechnicalQuestions();
            businessQueue = generateDefaultBusinessQuestions();
        } else {
            // 聚合 + 重新编号
            technicalQueue = aggregateAndIndex(technicalBasic, project);
            businessQueue = aggregateAndIndex(business, softSkill);
        }

        log.info("题目聚合完成：技术轮 {} 题，业务轮 {} 题", technicalQueue.size(), businessQueue.size());
        log.debug("技术轮题目：{}", JSONUtil.toJsonStr(technicalQueue));
        log.debug("业务轮题目：{}", JSONUtil.toJsonStr(businessQueue));

        // 返回状态更新
        Map<String, Object> updates = new HashMap<>();
        updates.put(BatchQuestionGenState.TECHNICAL_QUESTIONS_QUEUE, technicalQueue);
        updates.put(BatchQuestionGenState.BUSINESS_QUESTIONS_QUEUE, businessQueue);
        updates.put(BatchQuestionGenState.CURRENT_TECHNICAL_INDEX, 0);
        updates.put(BatchQuestionGenState.CURRENT_BUSINESS_INDEX, 0);
        updates.put(BatchQuestionGenState.FALLBACK, needsFallback);

        return CompletableFuture.completedFuture(updates);
    }

    /**
     * 聚合题目列表并重新编号
     */
    @SafeVarargs
    private List<GeneratedQuestion> aggregateAndIndex(List<GeneratedQuestion>... lists) {
        List<GeneratedQuestion> result = new ArrayList<>();
        int index = 1;
        for (List<GeneratedQuestion> list : lists) {
            if (list != null) {
                for (GeneratedQuestion q : list) {
                    result.add(GeneratedQuestion.builder()
                            .question(q.getQuestion())
                            .questionType(q.getQuestionType())
                            .expectedKeywords(q.getExpectedKeywords())
                            .difficulty(q.getDifficulty())
                            .reason(q.getReason())
                            .questionIndex(index++)
                            .build());
                }
            }
        }
        return result;
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