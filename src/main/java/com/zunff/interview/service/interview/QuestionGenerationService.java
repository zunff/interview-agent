package com.zunff.interview.service.interview;

import com.zunff.interview.config.KnowledgeConfig;
import com.zunff.interview.config.PromptConfig;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.model.dto.GeneratedQuestion;
import com.zunff.interview.model.dto.llm.resp.LlmQuestionListResultDto;
import com.zunff.interview.model.dto.rag.KnowledgeSearchResult;
import com.zunff.interview.service.extend.PromptTemplateService;
import com.zunff.interview.agent.state.BatchQuestionGenState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 题目生成服务
 * 负责批量生成指定类型的面试题目
 *
 * 功能：
 * - 根据题目类型和数量生成面试题目
 * - 支持知识库检索参考题目
 * - 支持降级处理（生成默认题目）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionGenerationService {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;
    private final PromptConfig promptConfig;
    private final InterviewKnowledgeService knowledgeService;
    private final KnowledgeConfig knowledgeConfig;

    /**
     * 批量生成指定类型的题目
     *
     * @param state 包含题目类型、数量等配置的状态对象
     * @return 生成结果，包含题目列表
     */
    public CompletableFuture<Map<String, Object>> execute(BatchQuestionGenState state, int count, QuestionType questionType, String outputKey) {
        log.info("批量生成 {} 题目，数量: {}", questionType.getDisplayName(), count);

        if (count <= 0) {
            log.info("{} 题目数量为 0，跳过生成", questionType.getDisplayName());
            return CompletableFuture.completedFuture(Map.of(outputKey, new ArrayList<GeneratedQuestion>()));
        }

        try {
            String candidateProfile = state.candidateProfile();
            String jobContext = state.jobContext();

            // 获取 prompt 模板（复用现有模板）
            String promptTemplateName = "question-generator-" + questionType.getEvaluationPrompt();
            String systemPrompt = promptTemplateService.getPrompt(promptTemplateName, Map.of(
                    "responseLanguage", promptConfig.getResponseLanguage(),
                    "count", count
            ));

            // 从知识库检索参考题目
            String referenceContext = "";
            if (knowledgeConfig.isEnabled()) {
                referenceContext = searchReferenceQuestions(jobContext, state, questionType);
            }

            // 构建 user prompt（复用现有模板）
            String userPrompt = promptTemplateService.getPrompt("question-generator-user", Map.ofEntries(
                    Map.entry("candidateProfile", candidateProfile == null ? "" : candidateProfile),
                    Map.entry("jobInfo", jobContext == null ? "" : jobContext),
                    Map.entry("roundDisplayName", questionType.getDisplayName()),
                    Map.entry("progressLabel", "Batch generation"),
                    Map.entry("doneCount", 0),
                    Map.entry("maxCount", count),
                    Map.entry("questionIndex", 1),
                    Map.entry("hasPreviousQuestions", false),
                    Map.entry("previousQuestions", "无"),
                    Map.entry("firstQuestionHint", ""),
                    Map.entry("referenceContext", referenceContext.isBlank() ? "None" : referenceContext),
                    Map.entry("responseLanguage", promptConfig.getResponseLanguage()),
                    Map.entry("count", count)
            ));

            ChatClient chatClient = chatClientBuilder.build();

            // 总是使用列表解析
            LlmQuestionListResultDto response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(LlmQuestionListResultDto.class);

            List<GeneratedQuestion> questions = parseQuestionList(response, questionType, count);

            log.info("批量生成 {} 题目成功，实际生成: {}", questionType.getDisplayName(), questions.size());

            return CompletableFuture.completedFuture(Map.of(outputKey, questions));

        } catch (Exception e) {
            log.error("批量生成 {} 题目失败", questionType.getDisplayName(), e);
            // 降级：返回默认题目
            List<GeneratedQuestion> fallbackQuestions = generateFallbackQuestions(count, questionType);
            return CompletableFuture.completedFuture(Map.of(outputKey, fallbackQuestions));
        }
    }

    /**
     * 从知识库检索参考题目
     */
    private String searchReferenceQuestions(String jobContext, BatchQuestionGenState state, QuestionType questionType) {
        try {
            List<KnowledgeSearchResult> results = knowledgeService.searchByJobInfo(
                    jobContext,
                    questionType.getDisplayName(),
                    state.knowledgeCompany(),
                    state.knowledgeJobPosition(),
                    5  // 批量生成时多检索一些参考
            );

            if (results.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                KnowledgeSearchResult r = results.get(i);
                sb.append(i + 1).append(". ").append(r.getQuestion()).append("\n");
                if (r.getCompany() != null && !r.getCompany().isEmpty()) {
                    sb.append("   公司：").append(r.getCompany()).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("知识库检索失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 解析 LLM 返回的题目列表
     */
    private List<GeneratedQuestion> parseQuestionList(LlmQuestionListResultDto response, QuestionType questionType, int expectedCount) {
        if (response == null || response.getQuestions() == null || response.getQuestions().isEmpty()) {
            return generateFallbackQuestions(expectedCount, questionType);
        }

        List<GeneratedQuestion> questions = new ArrayList<>();
        int index = 1;
        for (LlmQuestionListResultDto.QuestionDto dto : response.getQuestions()) {
            GeneratedQuestion question = GeneratedQuestion.builder()
                    .question(dto.getQuestion() == null || dto.getQuestion().isBlank()
                            ? buildFallbackQuestionText()
                            : dto.getQuestion())
                    .questionType(questionType.getDisplayName())
                    .expectedKeywords(dto.getExpectedKeywords() == null ? List.of() : dto.getExpectedKeywords())
                    .difficulty(dto.getDifficulty() == null || dto.getDifficulty().isBlank() ? "medium" : dto.getDifficulty())
                    .reason(dto.getReason() == null ? "" : dto.getReason())
                    .questionIndex(index++)
                    .build();
            questions.add(question);
        }

        // 如果生成数量不足，补充默认题目
        while (questions.size() < expectedCount) {
            questions.add(createFallbackQuestion(questions.size() + 1, questionType));
        }

        return questions;
    }

    private GeneratedQuestion createFallbackQuestion(int index, QuestionType questionType) {
        return GeneratedQuestion.builder()
                .question(buildFallbackQuestionText())
                .questionType(questionType.getDisplayName())
                .expectedKeywords(List.of())
                .difficulty("medium")
                .reason("Fallback question due to generation failure")
                .questionIndex(index)
                .build();
    }

    private List<GeneratedQuestion> generateFallbackQuestions(int count, QuestionType questionType) {
        List<GeneratedQuestion> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            questions.add(createFallbackQuestion(i + 1, questionType));
        }
        return questions;
    }

    private String buildFallbackQuestionText() {
        String language = promptConfig.getResponseLanguage();
        if (language != null && language.toLowerCase().startsWith("zh")) {
            return "请简单介绍一下你的技术背景和项目经验。";
        }
        return "Could you briefly introduce your technical background and project experience?";
    }
}
