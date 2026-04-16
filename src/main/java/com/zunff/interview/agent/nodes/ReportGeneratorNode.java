package com.zunff.interview.agent.nodes;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.model.dto.llm.vars.ReportUserPromptVars;
import com.zunff.interview.model.entity.EvaluationRecord;
import com.zunff.interview.service.EvaluationRecordService;
import com.zunff.interview.service.extend.PromptTemplateService;
import com.zunff.interview.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 报告生成节点
 * 面试结束后生成综合评估报告
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportGeneratorNode {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;
    private final EvaluationRecordService evaluationRecordService;

    /**
     * 执行报告生成
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始生成面试报告");

        String sessionId = state.sessionId();
        String candidateProfile = state.candidateProfile();

        // 优先使用岗位分析结果
        String jobContext = state.hasJobAnalysisResult()
                ? state.jobAnalysisResult().generateJobSummary()
                : state.jobInfo();

        // 从数据库读取评估记录（避免状态冗余）
        List<EvaluationRecord> evaluations = evaluationRecordService.getBySessionId(sessionId);

        // 获取轮次信息
        int technicalQuestionsDone = state.technicalQuestionsDone();
        int businessQuestionsDone = state.businessQuestionsDone();
        double technicalAvgScore = state.technicalAverageScore();
        double businessAvgScore = state.businessAverageScore();

        // 计算各维度平均分
        double avgScore = 0;
        double avgAccuracy = 0, avgLogic = 0, avgFluency = 0, avgConfidence = 0;
        double avgEmotion = 0, avgBodyLanguage = 0, avgVoiceTone = 0;
        int totalQuestions = evaluations.size();

        if (totalQuestions > 0) {
            int totalScore = 0, totalAccuracy = 0, totalLogic = 0, totalFluency = 0, totalConfidence = 0;
            int totalEmotion = 0, totalBodyLanguage = 0, totalVoiceTone = 0;

            for (EvaluationRecord eval : evaluations) {
                if (eval.getOverallScore() != null) totalScore += eval.getOverallScore();
                if (eval.getAccuracy() != null) totalAccuracy += eval.getAccuracy();
                if (eval.getLogic() != null) totalLogic += eval.getLogic();
                if (eval.getFluency() != null) totalFluency += eval.getFluency();
                if (eval.getConfidence() != null) totalConfidence += eval.getConfidence();
                if (eval.getEmotionScore() != null) totalEmotion += eval.getEmotionScore();
                if (eval.getBodyLanguageScore() != null) totalBodyLanguage += eval.getBodyLanguageScore();
                if (eval.getVoiceToneScore() != null) totalVoiceTone += eval.getVoiceToneScore();
            }

            avgScore = (double) totalScore / totalQuestions;
            avgAccuracy = (double) totalAccuracy / totalQuestions;
            avgLogic = (double) totalLogic / totalQuestions;
            avgFluency = (double) totalFluency / totalQuestions;
            avgConfidence = (double) totalConfidence / totalQuestions;
            avgEmotion = (double) totalEmotion / totalQuestions;
            avgBodyLanguage = (double) totalBodyLanguage / totalQuestions;
            avgVoiceTone = (double) totalVoiceTone / totalQuestions;
        }

        // 构建详细的评估摘要
        StringBuilder evalSummary = new StringBuilder();
        for (int i = 0; i < evaluations.size(); i++) {
            EvaluationRecord eval = evaluations.get(i);
            evalSummary.append("\n### 问题 ").append(i + 1).append("\n");
            evalSummary.append("- 问题：").append(eval.getQuestion()).append("\n");
            evalSummary.append("- 回答摘要：").append(truncate(eval.getAnswer(), 150)).append("\n");
            evalSummary.append("- 综合得分：").append(eval.getOverallScore()).append("\n");
            evalSummary.append("  - 内容维度：准确度 ").append(eval.getAccuracy());
            evalSummary.append(" | 逻辑性 ").append(eval.getLogic());
            evalSummary.append(" | 流畅度 ").append(eval.getFluency());
            evalSummary.append(" | 自信度 ").append(eval.getConfidence()).append("\n");
            evalSummary.append("  - 多模态：表情 ").append(eval.getEmotionScore());
            evalSummary.append(" | 肢体 ").append(eval.getBodyLanguageScore());
            evalSummary.append(" | 语调 ").append(eval.getVoiceToneScore()).append("\n");
            if (eval.getStrengths() != null && !eval.getStrengths().isEmpty()) {
                evalSummary.append("  - 优点：").append(String.join("、", eval.getStrengths())).append("\n");
            }
            if (eval.getWeaknesses() != null && !eval.getWeaknesses().isEmpty()) {
                evalSummary.append("  - 不足：").append(String.join("、", eval.getWeaknesses())).append("\n");
            }
        }

        // 从模板加载 system prompt
        String systemPrompt = promptTemplateService.getPrompt("report-generator");

        String evalSummaryText = evalSummary.length() == 0 ? "无评估记录" : evalSummary.toString();
        String userPrompt = promptTemplateService.getPrompt("report-generator-user",
                new ReportUserPromptVars(
                        candidateProfile,
                        jobContext,
                        technicalQuestionsDone,
                        businessQuestionsDone,
                        technicalAvgScore,
                        businessAvgScore,
                        avgScore,
                        avgAccuracy,
                        avgLogic,
                        avgFluency,
                        avgConfidence,
                        avgEmotion,
                        avgBodyLanguage,
                        avgVoiceTone,
                        totalQuestions,
                        evalSummaryText
                ).asMap());

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String report = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            // 添加总体评分到报告开头
            StringBuilder reportHeader = new StringBuilder();
            reportHeader.append("# 面试评估报告\n\n");
            reportHeader.append("> **综合得分**：").append(String.format("%.1f", avgScore)).append(" / 100\n");
            reportHeader.append("> **面试问题数**：").append(totalQuestions).append("\n\n");

            // 各维度平均分概览
            reportHeader.append("## 综合评估概览\n\n");
            reportHeader.append("### 内容维度\n");
            reportHeader.append("| 准确度 | 逻辑性 | 流畅度 | 自信度 |\n");
            reportHeader.append("|--------|--------|--------|--------|\n");
            reportHeader.append(String.format("| %.1f | %.1f | %.1f | %.1f |\n\n",
                    avgAccuracy, avgLogic, avgFluency, avgConfidence));

            reportHeader.append("### 多模态维度\n");
            reportHeader.append("| 表情表现 | 肢体语言 | 语音语调 |\n");
            reportHeader.append("|----------|----------|----------|\n");
            reportHeader.append(String.format("| %.1f | %.1f | %.1f |\n\n",
                    avgEmotion, avgBodyLanguage, avgVoiceTone));

            // 轮次表现摘要
            reportHeader.append("## 轮次表现\n\n");
            reportHeader.append("| 轮次 | 问题数 | 平均分 |\n");
            reportHeader.append("|------|--------|--------|\n");
            reportHeader.append(String.format("| 技术轮 | %d | %.1f |\n", technicalQuestionsDone, technicalAvgScore));
            reportHeader.append(String.format("| 业务轮 | %d | %.1f |\n", businessQuestionsDone, businessAvgScore));
            reportHeader.append(String.format("| **总计** | %d | %.1f |\n\n", totalQuestions, avgScore));

            reportHeader.append("---\n\n");

            String fullReport = reportHeader + report;

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.FINAL_REPORT, fullReport);
            updates.put(InterviewState.IS_FINISHED, true);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("面试报告生成完成，平均得分: {}", avgScore);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("生成报告失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            updates.put(InterviewState.FINAL_REPORT, "报告生成失败，请稍后重试。");
            updates.put(InterviewState.IS_FINISHED, true);
            return CompletableFuture.completedFuture(updates);
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

}