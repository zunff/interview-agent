package com.zunff.agent.agent.nodes;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.agent.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
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

    private static final String SYSTEM_PROMPT = """
            你是一位资深的面试官，需要根据候选人的回答质量决定是否进行追问。

            追问的目的是：
            1. 深入了解候选人的真实水平
            2. 验证回答的准确性
            3. 考察候选人的思维深度

            何时需要追问：
            - 回答不够具体，需要更多细节
            - 回答模糊或有疑点，需要澄清
            - 回答优秀，想深入挖掘更多能力
            - 回答与简历描述不符，需要验证

            何时不需追问：
            - 回答已经很完整和清晰
            - 候选人明显不熟悉该领域（继续追问无意义）
            - 已经追问两次以上

            请以 JSON 格式返回决策：
            {
                "needFollowUp": true,
                "followUpQuestion": "追问的问题内容",
                "reason": "为什么需要/不需要追问"
            }
            """;

    /**
     * 执行追问决策
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始追问决策，当前追问次数: {}", state.followUpCount());

        String question = state.currentQuestion();
        String answerText = state.answerText();
        @SuppressWarnings("unchecked")
        Map<String, Object> evaluation = (Map<String, Object>) state.data().get(InterviewState.CURRENT_EVALUATION);
        int followUpCount = state.followUpCount();
        int maxFollowUps = state.maxFollowUps();

        // 检查是否已达到最大追问次数
        if (followUpCount >= maxFollowUps) {
            log.info("已达到最大追问次数 {}，不再追问", maxFollowUps);
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.NEED_FOLLOW_UP, false);
            return CompletableFuture.completedFuture(updates);
        }

        // 构建提示
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("原始问题：").append(question).append("\n\n");
        userPrompt.append("候选人回答：").append(answerText).append("\n\n");

        if (evaluation != null) {
            userPrompt.append("评估结果：\n");
            userPrompt.append("- 综合得分：").append(evaluation.get("overallScore")).append("\n");
            userPrompt.append("- 准确性：").append(evaluation.get("accuracy")).append("\n");
            userPrompt.append("- 逻辑性：").append(evaluation.get("logic")).append("\n");

            @SuppressWarnings("unchecked")
            java.util.List<String> weaknesses = (java.util.List<String>) evaluation.get("weaknesses");
            if (weaknesses != null && !weaknesses.isEmpty()) {
                userPrompt.append("- 不足之处：").append(String.join("、", weaknesses)).append("\n");
            }
        }

        userPrompt.append("\n已追问次数：").append(followUpCount).append("/").append(maxFollowUps);
        userPrompt.append("\n请决定是否需要追问，如需要请提供追问问题。");

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt.toString())
                    .call()
                    .content();

            // 解析响应
            FollowUpResult result = parseFollowUpResult(response);
            result.followUpCount = followUpCount + 1; // 增加追问计数

            Map<String, Object> updates = new HashMap<>();
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
