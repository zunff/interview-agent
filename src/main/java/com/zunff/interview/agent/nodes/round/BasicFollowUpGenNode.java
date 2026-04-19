package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.config.PromptConfig;
import com.zunff.interview.constant.QuestionType;
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
 * 生成追问节点
 * 自己生成追问问题（不再依赖 FollowUpDecisionNode）
 * 基于 EvaluationBO 和 GeneratedQuestion 元信息生成针对性追问
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BasicFollowUpGenNode {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;
    private final PromptConfig promptConfig;

    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始生成追问问题");

        EvaluationBO evaluation = state.getCurrentEvaluation();
        GeneratedQuestion generatedQuestion = state.getCurrentGeneratedQuestion();

        try {
            ChatClient chatClient = chatClientBuilder.build();
            FollowUpQuestionResponseDto response = generateFollowUpQuestion(evaluation, generatedQuestion, state.formatFollowUpChain(), chatClient, state);
            String followUpQuestion = response.getFollowUpQuestion();
            log.info("生成追问: {}", followUpQuestion);

            // 构建 GeneratedQuestion 元数据对象
            GeneratedQuestion followUpMeta = GeneratedQuestion.builder()
                    .question(followUpQuestion)
                    .questionType(QuestionType.FOLLOW_UP.getDisplayName())
                    .expectedKeywords(response.getExpectedKeywords() != null ? response.getExpectedKeywords() : java.util.List.of())
                    .difficulty(response.getDifficulty() != null ? response.getDifficulty() : "medium")
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
            queue.addFirst(followUpMeta);  // 插入到队头

            log.info("生成追问并插入队头: {}", followUpQuestion);

            Map<String, Object> updates = new HashMap<>();
            updates.put(queueKey, queue);  // 更新队列
            updates.put(InterviewState.CURRENT_GENERATED_QUESTION, followUpMeta);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("追问次数累加: {} -> {}", state.followUpCount(), state.followUpCount() + 1);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("生成追问失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            // 失败时进入下一题
            updates.put(InterviewState.DECISION, RouteDecision.NEXT_QUESTION.getValue());
            return CompletableFuture.completedFuture(updates);
        }
    }

    /**
     * 生成针对性追问（包含追问链路上下文）
     */
    private FollowUpQuestionResponseDto generateFollowUpQuestion(EvaluationBO evaluation, GeneratedQuestion question, String followUpChain, ChatClient chatClient, InterviewState state) {
        Map<String, Object> promptVars = buildFollowUpGenerationPromptVars(evaluation, question, followUpChain, state);

        String systemPrompt = promptTemplateService.getPrompt("followup-question-generation", promptVars);
        String userPrompt = promptTemplateService.getPrompt("followup-question-generation-user", promptVars);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(FollowUpQuestionResponseDto.class);
    }

    private Map<String, Object> buildFollowUpGenerationPromptVars(
            EvaluationBO evaluation,
            GeneratedQuestion question,
            String followUpChain,
            InterviewState state) {

        Map<String, Object> promptVars = new HashMap<>();
        promptVars.put("responseLanguage", promptConfig.getResponseLanguage());
        promptVars.put("followUpChain", followUpChain != null ? followUpChain : "");

        // 添加主问题上下文（与 ChallengeFollowUpGenNode 和 DeepDiveFollowUpGenNode 保持一致）
        String originalQuestion = state.getMainGeneratedQuestion() != null
                ? state.getMainGeneratedQuestion().getQuestion()
                : state.currentQuestion();
        promptVars.put("originalQuestion", originalQuestion != null ? originalQuestion : "");

        if (question != null) {
            promptVars.put("question", question.getQuestion() != null ? question.getQuestion() : "");
            promptVars.put("expectedKeywords", question.getExpectedKeywords() != null ? String.join(", ", question.getExpectedKeywords()) : "");
            promptVars.put("difficulty", question.getDifficulty() != null ? question.getDifficulty() : "medium");
            promptVars.put("questionIntent", question.getReason() != null ? question.getReason() : "No specific intention stated");
            promptVars.put("questionType", question.getQuestionType() != null ? question.getQuestionType() : "unknown");
        } else {
            promptVars.put("question", evaluation.getQuestion() != null ? evaluation.getQuestion() : "");
            promptVars.put("expectedKeywords", "");
            promptVars.put("difficulty", "medium");
            promptVars.put("questionIntent", "No specific intention stated");
            promptVars.put("questionType", "unknown");
        }

        promptVars.put("answer", evaluation.getAnswer() != null ? evaluation.getAnswer() : "");
        promptVars.put("overallScore", evaluation.getOverallScore());
        promptVars.put("accuracy", evaluation.getAccuracy());
        promptVars.put("logic", evaluation.getLogic());
        promptVars.put("fluency", evaluation.getFluency());
        promptVars.put("confidence", evaluation.getConfidence());

        promptVars.put("strengths", formatBulletList(evaluation.getStrengths()));
        promptVars.put("weaknesses", formatBulletList(evaluation.getWeaknesses()));
        promptVars.put("detailedEvaluation",
                evaluation.getDetailedEvaluation() != null ? evaluation.getDetailedEvaluation() : "None provided.");

        promptVars.put("emotionScore", evaluation.getEmotionScore());
        promptVars.put("bodyLanguageScore", evaluation.getBodyLanguageScore());
        promptVars.put("voiceToneScore", evaluation.getVoiceToneScore());
        promptVars.put("modalityConcern", evaluation.isModalityConcern());

        return promptVars;
    }

    private static String formatBulletList(java.util.List<String> items) {
        if (CollectionUtils.isEmpty(items)) {
            return "None provided.";
        }
        return "- " + String.join("\n- ", items);
    }
}
