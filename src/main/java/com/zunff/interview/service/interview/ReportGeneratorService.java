package com.zunff.interview.service.interview;

import com.zunff.interview.service.extend.PromptTemplateService;
import com.zunff.interview.utils.JsonExtractionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

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
        String userPrompt = promptTemplateService.getPrompt("question-analysis-user", Map.of(
                "question", question == null ? "" : question,
                "questionType", questionType == null ? "" : questionType,
                "jobInfo", jobInfo == null ? "" : jobInfo,
                "resumeSummary", buildResumeSummary(resume)
        ));

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
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
     * 解析分析结果
     */
    private QuestionAnalysisResult parseAnalysisResult(String response) {
        try {
            String jsonStr = JsonExtractionUtils.extractJsonObjectString(response);
            cn.hutool.json.JSONObject json = cn.hutool.json.JSONUtil.parseObj(jsonStr);

            return new QuestionAnalysisResult(
                    json.getStr("interviewIntent", ""),
                    json.getStr("standardAnswer", "")
            );
        } catch (Exception e) {
            return new QuestionAnalysisResult("分析失败", response);
        }
    }

    private String buildResumeSummary(String resume) {
        if (resume == null || resume.isEmpty()) {
            return "无";
        }
        return resume.substring(0, Math.min(resume.length(), 500));
    }

    /**
     * 问题分析结果
     */
    public record QuestionAnalysisResult(
            String interviewIntent,
            String standardAnswer
    ) {}
}
