package com.zunff.interview.service.interview;

import com.zunff.interview.service.extend.PromptTemplateService;
import com.zunff.interview.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 报告生成服务
 * 生成面试综合报告
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGeneratorService {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;

    /**
     * 生成问题分析
     * 包括考察意图和标准答案
     */
    public CompletableFuture<QuestionAnalysisResult> generateQuestionAnalysis(
            String question,
            String questionType,
            String jobInfo,
            String resume) {

        log.debug("生成问题分析: {}", question);

        String systemPrompt = promptTemplateService.getPrompt("question-analysis");

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("面试问题：").append(question).append("\n\n");
        userPrompt.append("问题类型：").append(questionType).append("\n\n");
        userPrompt.append("应聘岗位：").append(jobInfo).append("\n\n");
        if (resume != null && !resume.isEmpty()) {
            userPrompt.append("候选人简历摘要：\n").append(resume.substring(0, Math.min(resume.length(), 500))).append("\n\n");
        }

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt.toString())
                    .call()
                    .content();

            return CompletableFuture.completedFuture(parseAnalysisResult(response));

        } catch (Exception e) {
            log.error("生成问题分析失败", e);
            return CompletableFuture.completedFuture(
                    new QuestionAnalysisResult("分析失败", "标准答案生成失败")
            );
        }
    }

    /**
     * 生成综合面试报告
     */
    public String generateComprehensiveReport(InterviewState state) {
        log.info("生成综合面试报告");

        StringBuilder report = new StringBuilder();

        // 基本信息
        report.append("# 面试综合评估报告\n\n");
        report.append("## 基本信息\n");
        report.append("- 应聘岗位：").append(state.jobInfo()).append("\n\n");

        // 面试问题与评估
        List<String> questions = state.questions();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> evaluations = (List<Map<String, Object>>) state.data()
                .getOrDefault(InterviewState.EVALUATIONS, List.of());

        report.append("## 面试问答记录\n\n");
        for (int i = 0; i < questions.size(); i++) {
            report.append("### 问题 ").append(i + 1).append("\n");
            report.append("**问题内容：** ").append(questions.get(i)).append("\n\n");

            if (i < evaluations.size()) {
                Map<String, Object> eval = evaluations.get(i);
                report.append("**评估结果：**\n");
                report.append("- 综合得分：").append(eval.getOrDefault("overallScore", "N/A")).append("\n");
                report.append("- 路由决策：").append(eval.getOrDefault("decision", "nextQuestion")).append("\n\n");
            }
        }

        // 综合评估
        report.append("## 综合评估\n\n");

        // 计算平均分
        double technicalAvg = state.technicalAverageScore();
        double businessAvg = state.businessAverageScore();
        double overallAvg = (technicalAvg + businessAvg) / 2;

        report.append("- 技术轮平均分：").append(String.format("%.1f", technicalAvg)).append("\n");
        report.append("- 业务轮平均分：").append(String.format("%.1f", businessAvg)).append("\n");
        report.append("- 综合平均分：").append(String.format("%.1f", overallAvg)).append("\n\n");

        // 建议
        report.append("## 录用建议\n\n");
        if (overallAvg >= 80) {
            report.append("**建议：强烈推荐录用**\n\n");
            report.append("候选人在面试中表现出色，技术能力和业务理解都达到较高水平。\n");
        } else if (overallAvg >= 70) {
            report.append("**建议：推荐录用**\n\n");
            report.append("候选人整体表现良好，具备岗位所需的核心能力。\n");
        } else if (overallAvg >= 60) {
            report.append("**建议：待定/需进一步考察**\n\n");
            report.append("候选人表现一般，建议结合其他候选人综合评估或进行二面深入考察。\n");
        } else {
            report.append("**建议：不推荐录用**\n\n");
            report.append("候选人在面试中表现欠佳，暂不建议进入下一轮面试。\n");
        }

        return report.toString();
    }

    /**
     * 解析分析结果
     */
    private QuestionAnalysisResult parseAnalysisResult(String response) {
        try {
            String jsonStr = extractJson(response);
            cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(jsonStr);

            return new QuestionAnalysisResult(
                    json.getStr("interviewIntent", ""),
                    json.getStr("standardAnswer", "")
            );
        } catch (Exception e) {
            return new QuestionAnalysisResult("分析失败", response);
        }
    }

    /**
     * 提取 JSON 字符串
     */
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    /**
     * 问题分析结果
     */
    public record QuestionAnalysisResult(
            String interviewIntent,
            String standardAnswer
    ) {}
}
