package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.config.PromptConfig;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.agent.state.InterviewState;
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
 * 挑战问题节点
 * 当候选人表现优秀（得分 > 90）时，生成更有挑战性的问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeQuestionNode {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;
    private final PromptConfig promptConfig;

    /**
     * 执行挑战问题生成
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始生成挑战问题，候选人表现优秀");

        String originalQuestion = state.currentQuestion();
        String answer = state.answerText();

        try {
            String systemPrompt = promptTemplateService.getPrompt("challenge-question", Map.of(
                    "responseLanguage", promptConfig.getResponseLanguage()
            ));

            String answerSummary = answer != null && answer.length() > 200
                    ? answer.substring(0, 200) + "..."
                    : (answer != null ? answer : "");

            String userPrompt = promptTemplateService.getPrompt("challenge-question-user", Map.of(
                    "originalQuestion", originalQuestion == null ? "" : originalQuestion,
                    "answerSummary", answerSummary,
                    "responseLanguage", promptConfig.getResponseLanguage()
            ));

            ChatClient chatClient = chatClientBuilder.build();

            String challengeQuestion = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.CURRENT_QUESTION, challengeQuestion);
            updates.put(InterviewState.QUESTION_TYPE, QuestionType.CHALLENGE_QUESTION.getDisplayName());
            updates.put(InterviewState.FOLLOW_UP_COUNT, state.followUpCount() + 1);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("生成挑战问题: {}", challengeQuestion);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("生成挑战问题失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            // 失败时进入下一题
            updates.put(InterviewState.DECISION, RouteDecision.NEXT_QUESTION.getValue());
            return CompletableFuture.completedFuture(updates);
        }
    }
}
