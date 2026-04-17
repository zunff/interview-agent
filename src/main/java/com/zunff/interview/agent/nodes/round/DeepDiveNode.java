package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.config.PromptConfig;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.service.extend.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 深入追问节点
 * 当候选人表现较差（得分 < 50）时，生成深入追问以确认问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepDiveNode {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;
    private final PromptConfig promptConfig;

    /**
     * 执行深入追问生成
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始生成深入追问，候选人需要进一步考察");

        String originalQuestion = state.currentQuestion();
        String answer = state.answerText();
        var evaluation = state.getCurrentEvaluation();

        try {
            String systemPrompt = promptTemplateService.getPrompt("deep-dive-question", Map.of(
                    "responseLanguage", promptConfig.getResponseLanguage()
            ));

            StringBuilder weaknessesText = new StringBuilder();
            if (evaluation != null && evaluation.getWeaknesses() != null) {
                for (String weakness : evaluation.getWeaknesses()) {
                    weaknessesText.append("- ").append(weakness).append("\n");
                }
            }

            // 获取期望关键词，用作提示引导
            String expectedKeywordsText = "None provided.";
            if (evaluation != null && evaluation.getGeneratedQuestion() != null
                    && evaluation.getGeneratedQuestion().getExpectedKeywords() != null
                    && !evaluation.getGeneratedQuestion().getExpectedKeywords().isEmpty()) {
                expectedKeywordsText = String.join(", ", evaluation.getGeneratedQuestion().getExpectedKeywords());
            }

            // 获取更新后的追问链路
            String followUpChainText = state.formatFollowUpChain();

            String userPrompt = promptTemplateService.getPrompt("deep-dive-question-user", Map.of(
                    "originalQuestion", originalQuestion == null ? "" : originalQuestion,
                    "answer", answer == null ? "" : answer,
                    "weaknesses", weaknessesText.isEmpty() ? "None provided." : weaknessesText.toString().trim(),
                    "expectedKeywords", expectedKeywordsText,
                    "followUpChain", followUpChainText,
                    "responseLanguage", promptConfig.getResponseLanguage()
            ));

            ChatClient chatClient = chatClientBuilder.build();

            String deepDiveQuestion = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.CURRENT_QUESTION, deepDiveQuestion);
            updates.put(InterviewState.QUESTION_TYPE, QuestionType.DEEP_DIVE.getDisplayName());
            updates.put(InterviewState.FOLLOW_UP_COUNT, state.followUpCount() + 1);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("生成深入追问: {}", deepDiveQuestion);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("生成深入追问失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            // 失败时进入下一题
            updates.put(InterviewState.DECISION, RouteDecision.NEXT_QUESTION.getValue());
            return CompletableFuture.completedFuture(updates);
        }
    }
}
