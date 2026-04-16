package com.zunff.interview.service.interview;

import com.zunff.interview.model.dto.llm.resp.QuestionAnalysisResultDto;
import com.zunff.interview.service.extend.PromptTemplateService;
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
    public CompletableFuture<QuestionAnalysisResultDto> generateQuestionAnalysis(
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

            QuestionAnalysisResultDto response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(QuestionAnalysisResultDto.class);

            return CompletableFuture.completedFuture(normalizeAnalysisResult(response));

        } catch (Exception e) {
            log.error("生成问题分析失败", e);
            return CompletableFuture.completedFuture(
                    new QuestionAnalysisResultDto("分析失败", "标准答案生成失败")
            );
        }
    }

    private QuestionAnalysisResultDto normalizeAnalysisResult(QuestionAnalysisResultDto result) {
        if (result == null) {
            return new QuestionAnalysisResultDto("分析失败", "");
        }
        return new QuestionAnalysisResultDto(
                result.interviewIntent() == null ? "" : result.interviewIntent(),
                result.standardAnswer() == null ? "" : result.standardAnswer()
        );
    }

    private String buildResumeSummary(String resume) {
        if (resume == null || resume.isEmpty()) {
            return "无";
        }
        return resume.substring(0, Math.min(resume.length(), 500));
    }


}
