package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.config.PromptConfig;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.bo.GeneratedQuestion;
import com.zunff.interview.model.dto.llm.resp.FollowUpQuestionResponseDto;
import com.zunff.interview.service.extend.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 挑战问题节点
 * 当候选人表现优秀（得分 > 90）时，生成更有挑战性的问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeFollowUpGenNode {

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
        EvaluationBO evaluation = state.getCurrentEvaluation();
        GeneratedQuestion meta = resolveGeneratedQuestionMeta(evaluation, state);

        try {
            Map<String, Object> promptVars = buildChallengePromptVars(
                    originalQuestion, answer, evaluation, meta, state.formatFollowUpChain());

            String systemPrompt = promptTemplateService.getPrompt("challenge-question", promptVars);
            String userPrompt = promptTemplateService.getPrompt("challenge-question-user", promptVars);

            ChatClient chatClient = chatClientBuilder.build();

            FollowUpQuestionResponseDto response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(FollowUpQuestionResponseDto.class);

            String challengeQuestion = response.getFollowUpQuestion();

            // 构建 GeneratedQuestion 元数据对象
            GeneratedQuestion challengeMeta = GeneratedQuestion.builder()
                    .question(challengeQuestion)
                    .questionType(QuestionType.CHALLENGE_QUESTION.getDisplayName())
                    .expectedKeywords(response.getExpectedKeywords() != null ? response.getExpectedKeywords() : java.util.List.of())
                    .difficulty(response.getDifficulty() != null ? response.getDifficulty() : "hard")
                    .reason(response.getReason() != null ? response.getReason() : "")
                    .questionIndex(state.questionIndex())  // 继承主问题的 questionIndex
                    .build();

            // 插入到对应轮次队列头部
            String queueKey = state.isTechnicalRound()
                    ? InterviewState.TECHNICAL_QUESTIONS_QUEUE
                    : InterviewState.BUSINESS_QUESTIONS_QUEUE;
            List<GeneratedQuestion> queue = new ArrayList<>(state.isTechnicalRound()
                    ? state.technicalQuestionsQueue()
                    : state.businessQuestionsQueue());
            queue.addFirst(challengeMeta);  // 插入到队头

            log.info("生成挑战题并插入队头: {}", challengeQuestion);

            Map<String, Object> updates = new HashMap<>();
            updates.put(queueKey, queue);  // 更新队列
            updates.put(InterviewState.CURRENT_GENERATED_QUESTION, challengeMeta);
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

    private static GeneratedQuestion resolveGeneratedQuestionMeta(EvaluationBO evaluation, InterviewState state) {
        if (evaluation != null && evaluation.getGeneratedQuestion() != null) {
            return evaluation.getGeneratedQuestion();
        }
        return state.getCurrentGeneratedQuestion();
    }

    private Map<String, Object> buildChallengePromptVars(
            String originalQuestion,
            String answer,
            EvaluationBO evaluation,
            GeneratedQuestion meta,
            String followUpChain) {

        Map<String, Object> promptVars = new HashMap<>();
        promptVars.put("responseLanguage", promptConfig.getResponseLanguage());
        promptVars.put("originalQuestion", originalQuestion != null ? originalQuestion : "");
        promptVars.put("answer", answer != null ? answer : "");
        promptVars.put("followUpChain", followUpChain != null ? followUpChain : "");

        promptVars.put("questionDifficulty", meta != null && meta.getDifficulty() != null ? meta.getDifficulty() : "medium");
        promptVars.put("questionIntent", meta != null && meta.getReason() != null ? meta.getReason() : "No specific intention stated");
        promptVars.put("questionType", meta != null && meta.getQuestionType() != null ? meta.getQuestionType() : "unknown");

        String expectedKw = "None provided.";
        if (meta != null && meta.getExpectedKeywords() != null && !meta.getExpectedKeywords().isEmpty()) {
            expectedKw = String.join(", ", meta.getExpectedKeywords());
        }
        promptVars.put("expectedKeywords", expectedKw);

        if (evaluation != null) {
            promptVars.put("overallScore", evaluation.getOverallScore());
            promptVars.put("accuracy", evaluation.getAccuracy());
            promptVars.put("logic", evaluation.getLogic());
            promptVars.put("fluency", evaluation.getFluency());
            promptVars.put("confidence", evaluation.getConfidence());
            promptVars.put("strengths", formatBulletList(evaluation.getStrengths()));
            promptVars.put("detailedEvaluation",
                    evaluation.getDetailedEvaluation() != null ? evaluation.getDetailedEvaluation() : "None provided.");
        } else {
            promptVars.put("overallScore", 0);
            promptVars.put("accuracy", 0);
            promptVars.put("logic", 0);
            promptVars.put("fluency", 0);
            promptVars.put("confidence", 0);
            promptVars.put("strengths", "None provided.");
            promptVars.put("detailedEvaluation", "None provided.");
        }

        return promptVars;
    }

    private static String formatBulletList(java.util.List<String> items) {
        if (CollectionUtils.isEmpty(items)) {
            return "None provided.";
        }
        return "- " + String.join("\n- ", items);
    }
}
