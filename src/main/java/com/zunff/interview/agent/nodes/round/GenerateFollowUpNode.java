package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.config.PromptConfig;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.dto.GeneratedQuestion;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.service.extend.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 生成追问节点
 * 自己生成追问问题（不再依赖 FollowUpDecisionNode）
 * 基于 EvaluationBO 和 GeneratedQuestion 元信息生成针对性追问
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GenerateFollowUpNode {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;
    private final PromptConfig promptConfig;

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始生成追问问题");

        EvaluationBO evaluation = state.getCurrentEvaluation();
        GeneratedQuestion generatedQuestion = state.getCurrentGeneratedQuestion();

        try {
            String followUpQuestion = generateFollowUpQuestion(evaluation, generatedQuestion);
            log.info("生成追问: {}", followUpQuestion);

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.CURRENT_QUESTION, followUpQuestion);
            updates.put(InterviewState.QUESTION_TYPE, QuestionType.FOLLOW_UP.getDisplayName());
            updates.put(InterviewState.FOLLOW_UP_COUNT, state.followUpCount() + 1);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("追问次数累加: {} -> {}", state.followUpCount(), state.followUpCount() + 1);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("生成追问失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            // 失败时进入下一题
            updates.put(InterviewState.DECISION, "nextQuestion");
            return CompletableFuture.completedFuture(updates);
        }
    }

    /**
     * 生成针对性追问
     */
    private String generateFollowUpQuestion(EvaluationBO evaluation, GeneratedQuestion question) {
        // 构建提示词变量
        Map<String, Object> promptVars = new HashMap<>();
        promptVars.put("responseLanguage", promptConfig.getResponseLanguage());

        if (question != null) {
            promptVars.put("question", question.getQuestion() != null ? question.getQuestion() : "");
            promptVars.put("expectedKeywords", question.getExpectedKeywords() != null ? String.join(", ", question.getExpectedKeywords()) : "");
            promptVars.put("difficulty", question.getDifficulty() != null ? question.getDifficulty() : "medium");
        } else {
            promptVars.put("question", evaluation.getQuestion() != null ? evaluation.getQuestion() : "");
            promptVars.put("expectedKeywords", "");
            promptVars.put("difficulty", "medium");
        }

        promptVars.put("weaknesses", evaluation.getWeaknesses() != null ? String.join("\n- ", evaluation.getWeaknesses()) : "无明显弱点");
        promptVars.put("modalitySuggestion", evaluation.getModalityFollowUpSuggestion() != null ? evaluation.getModalityFollowUpSuggestion() : "");

        String systemPrompt = promptTemplateService.getPrompt("followup-question-generation", promptVars);

        ChatClient chatClient = chatClientBuilder.build();
        return chatClient.prompt()
                .system(systemPrompt)
                .call()
                .content();
    }
}
