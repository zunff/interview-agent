package com.zunff.agent.agent.nodes;

import com.zunff.agent.state.InterviewState;
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

    private static final String SYSTEM_PROMPT = """
            你是一位资深的面试评估专家，需要根据面试记录生成综合评估报告。

            报告需要包含以下内容：

            ## 一、总体评价
            - 面试表现概述
            - 综合得分（0-100分）

            ## 二、各维度评估
            - 技术能力评估
            - 项目经验评估
            - 沟通表达能力
            - 专业素养

            ## 三、优势与亮点
            列出候选人的主要优势

            ## 四、待提升方面
            列出需要改进的地方

            ## 五、面试建议
            给候选人的改进建议

            ## 六、录用建议
            - 推荐等级：强烈推荐/推荐/一般/不推荐
            - 适用岗位建议

            请以 Markdown 格式输出报告内容。
            """;

    /**
     * 执行报告生成
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始生成面试报告");

        String resume = state.resume();
        String jobInfo = state.jobInfo();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evaluations = (List<Map<String, Object>>) state.data()
                .getOrDefault(InterviewState.EVALUATIONS, List.of());

        // 构建评估摘要
        StringBuilder evalSummary = new StringBuilder();
        int totalScore = 0;
        int totalQuestions = evaluations.size();

        for (int i = 0; i < evaluations.size(); i++) {
            Map<String, Object> eval = evaluations.get(i);
            evalSummary.append("\n### 问题 ").append(i + 1).append("\n");
            evalSummary.append("- 问题：").append(eval.get("question")).append("\n");
            evalSummary.append("- 回答摘要：").append(truncate(eval.get("answer"), 100)).append("\n");
            evalSummary.append("- 综合得分：").append(eval.get("overallScore")).append("\n");

            if (eval.get("overallScore") instanceof Number) {
                totalScore += ((Number) eval.get("overallScore")).intValue();
            }
        }

        double avgScore = totalQuestions > 0 ? (double) totalScore / totalQuestions : 0;

        // 构建用户提示
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("## 候选人简历\n").append(resume).append("\n\n");
        userPrompt.append("## 应聘岗位\n").append(jobInfo).append("\n\n");
        userPrompt.append("## 面试评估记录\n");
        userPrompt.append("- 问题总数：").append(totalQuestions).append("\n");
        userPrompt.append("- 平均得分：").append(String.format("%.1f", avgScore)).append("\n");
        userPrompt.append(evalSummary).append("\n\n");
        userPrompt.append("请根据以上面试记录生成综合评估报告。");

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String report = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt.toString())
                    .call()
                    .content();

            // 添加总体评分到报告开头
            String fullReport = String.format(
                    "# 面试评估报告\n\n" +
                    "> **综合得分**：%.1f / 100\n" +
                    "> **面试问题数**：%d\n\n" +
                    "---\n\n%s",
                    avgScore, totalQuestions, report
            );

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.FINAL_REPORT, fullReport);
            updates.put(InterviewState.IS_FINISHED, true);

            log.info("面试报告生成完成，平均得分: {}", avgScore);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("生成报告失败", e);
            Map<String, Object> updates = new HashMap<>();
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