package com.zunff.interview.agent.nodes;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.constant.InterviewRound;
import com.zunff.interview.model.bo.EvaluationBO;
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
 * 追问决策节点
 * 根据评估结果决定是否需要追问，并生成追问问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpDecisionNode {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;

    /**
     * 执行追问决策
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始追问决策，当前轮次: {}, 追问次数: {}", state.currentRound(), state.followUpCount());

        String question = state.currentQuestion();
        String answerText = state.answerText();
        EvaluationBO evaluation = (EvaluationBO) state.data().get(InterviewState.CURRENT_EVALUATION);
        int followUpCount = state.followUpCount();
        int maxFollowUps = state.maxFollowUpsForCurrentRound();
        InterviewRound round = state.currentRoundEnum();
        String modalitySuggestion = state.modalityFollowUpSuggestion();

        // 检查是否已达到最大追问次数
        if (followUpCount >= maxFollowUps) {
            log.info("已达到当前轮次最大追问次数 {}，不再追问", maxFollowUps);
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.NEED_FOLLOW_UP, false);
            return CompletableFuture.completedFuture(updates);
        }

        // 从模板加载 system prompt
        String systemPrompt = promptTemplateService.getPrompt("followup-decision");

        // 构建提示
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("原始问题：").append(question).append("\n\n");
        userPrompt.append("候选人回答：").append(answerText).append("\n\n");
        userPrompt.append("当前轮次：").append(round.getDisplayName()).append("\n\n");

        if (evaluation != null) {
            userPrompt.append("评估结果：\n");
            userPrompt.append("- 综合得分：").append(evaluation.getOverallScore()).append("\n");
            userPrompt.append("- 准确性：").append(evaluation.getAccuracy()).append("\n");
            userPrompt.append("- 逻辑性：").append(evaluation.getLogic()).append("\n");
            userPrompt.append("- 流畅度：").append(evaluation.getFluency()).append("\n");
            userPrompt.append("- 自信度：").append(evaluation.getConfidence()).append("\n");

           List<String> weaknesses = evaluation.getWeaknesses();
            if (weaknesses != null && !weaknesses.isEmpty()) {
                userPrompt.append("- 不足之处：").append(String.join("、", weaknesses)).append("\n");
            }
        }

        // 添加多模态建议
        if (modalitySuggestion != null && !modalitySuggestion.isEmpty()) {
            userPrompt.append("\n多模态观察：").append(modalitySuggestion).append("\n");
        }

        userPrompt.append("\n剩余追问配额：").append(maxFollowUps - followUpCount).append("/").append(maxFollowUps);
        userPrompt.append("\n请决定是否需要追问，如需要请提供追问问题。");

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt.toString())
                    .call()
                    .content();

            // 解析响应
            FollowUpResult result = parseFollowUpResult(response);
            result.followUpCount = followUpCount + 1; // 增加追问计数

            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.recordSuccess(updates);
            updates.put(InterviewState.NEED_FOLLOW_UP, result.needFollowUp);

            if (result.needFollowUp && result.followUpQuestion != null) {
                updates.put(InterviewState.FOLLOW_UP_QUESTION, result.followUpQuestion);
                updates.put(InterviewState.FOLLOW_UP_COUNT, result.followUpCount);
                log.info("决定追问: {}", result.followUpQuestion);
            } else {
                log.info("决定不追问: {}", result.reason);
            }

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("追问决策失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            updates.put(InterviewState.NEED_FOLLOW_UP, false);
            return CompletableFuture.completedFuture(updates);
        }
    }

    private FollowUpResult parseFollowUpResult(String response) {
        try {
            String jsonStr = extractJson(response);
            JSONObject json = JSONUtil.parseObj(jsonStr);

            return FollowUpResult.builder()
                    .needFollowUp(json.getBool("needFollowUp", false))
                    .followUpQuestion(json.getStr("followUpQuestion"))
                    .reason(json.getStr("reason", ""))
                    .build();
        } catch (Exception e) {
            log.error("解析追问结果失败: {}", e.getMessage());
            return FollowUpResult.builder()
                    .needFollowUp(false)
                    .reason("解析失败")
                    .build();
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class FollowUpResult {
        private boolean needFollowUp;
        private String followUpQuestion;
        private String reason;
        private int followUpCount;
    }
}
