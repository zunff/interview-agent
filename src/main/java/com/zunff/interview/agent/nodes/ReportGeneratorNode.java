package com.zunff.interview.agent.nodes;

import com.zunff.interview.agent.CircuitBreakerHelper;
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

    /**
     * 执行报告生成
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始生成面试报告");

        String candidateProfile = state.candidateProfile();
        String jobInfo = state.jobInfo();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evaluations = (List<Map<String, Object>>) state.data()
                .getOrDefault(InterviewState.EVALUATIONS, List.of());

        // 获取轮次信息
        int technicalQuestionsDone = state.technicalQuestionsDone();
        int businessQuestionsDone = state.businessQuestionsDone();
        double technicalAvgScore = state.technicalAverageScore();
        double businessAvgScore = state.businessAverageScore();

        // 计算总分
        int totalScore = 0;
        int totalQuestions = evaluations.size();

        for (Map<String, Object> eval : evaluations) {
            if (eval.get("overallScore") instanceof Number) {
                totalScore += ((Number) eval.get("overallScore")).intValue();
            }
        }

        double avgScore = totalQuestions > 0 ? (double) totalScore / totalQuestions : 0;

        // 构建评估摘要
        StringBuilder evalSummary = new StringBuilder();
        for (int i = 0; i < evaluations.size(); i++) {
            Map<String, Object> eval = evaluations.get(i);
            evalSummary.append("\n### 问题 ").append(i + 1).append("\n");
            evalSummary.append("- 问题：").append(eval.get("question")).append("\n");
            evalSummary.append("- 回答摘要：").append(truncate(eval.get("answer"), 100)).append("\n");
            evalSummary.append("- 综合得分：").append(eval.get("overallScore")).append("\n");
        }

        // 从模板加载 system prompt
        String systemPrompt = promptTemplateService.getPrompt("report-generator");

        // 构建用户提示
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("## 候选人画像\n").append(candidateProfile).append("\n\n");
        userPrompt.append("## 应聘岗位\n").append(jobInfo).append("\n\n");

        // 轮次表现
        userPrompt.append("## 轮次表现\n");
        userPrompt.append("- **技术轮**：平均分 ").append(String.format("%.1f", technicalAvgScore)).append("/100 (").append(technicalQuestionsDone).append("题)\n");
        userPrompt.append("- **业务轮**：平均分 ").append(String.format("%.1f", businessAvgScore)).append("/100 (").append(businessQuestionsDone).append("题)\n");
        userPrompt.append("- **总平均**：").append(String.format("%.1f", avgScore)).append("/100\n\n");

        userPrompt.append("## 面试评估记录\n");
        userPrompt.append("- 问题总数：").append(totalQuestions).append("\n");
        userPrompt.append(evalSummary).append("\n\n");
        userPrompt.append("请根据以上面试记录生成综合评估报告。");

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String report = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt.toString())
                    .call()
                    .content();

            // 添加总体评分到报告开头
            StringBuilder reportHeader = new StringBuilder();
            reportHeader.append("# 面试评估报告\n\n");
            reportHeader.append("> **综合得分**：").append(String.format("%.1f", avgScore)).append(" / 100\n");
            reportHeader.append("> **面试问题数**：").append(totalQuestions).append("\n\n");

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

    private String truncate(Object text, int maxLength) {
        if (text == null) return "";
        String str = text.toString();
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }
}