package com.zunff.interview.agent.nodes;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.state.InterviewState;
import com.zunff.interview.service.PromptTemplateService;
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

    /**
     * 执行深入追问生成
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始生成深入追问，候选人需要进一步考察");

        String originalQuestion = state.currentQuestion();
        String answer = state.answerText();
        var evaluation = state.getCurrentEvaluation();

        try {
            String systemPrompt = promptTemplateService.getPrompt("deep-dive-question");

            StringBuilder weaknessesText = new StringBuilder();
            if (evaluation != null && evaluation.getWeaknesses() != null) {
                for (String weakness : evaluation.getWeaknesses()) {
                    weaknessesText.append("- ").append(weakness).append("\n");
                }
            }

            String userPrompt = String.format(
                    "原始问题：%s\n\n候选人回答：%s\n\n回答的不足之处：\n%s\n\n" +
                    "请生成一个深入追问问题，帮助确认候选人是否真的存在这些不足，" +
                    "或者只是表达问题。追问应该针对具体的薄弱环节。",
                    originalQuestion,
                    answer != null ? answer : "",
                    weaknessesText.toString());

            ChatClient chatClient = chatClientBuilder.build();

            String deepDiveQuestion = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.CURRENT_QUESTION, deepDiveQuestion);
            updates.put(InterviewState.QUESTION_TYPE, "深入追问");
            updates.put(InterviewState.FOLLOW_UP_COUNT, state.followUpCount() + 1);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("生成深入追问: {}", deepDiveQuestion);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("生成深入追问失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            // 失败时跳过追问
            updates.put(InterviewState.NEED_FOLLOW_UP, false);
            return CompletableFuture.completedFuture(updates);
        }
    }
}
