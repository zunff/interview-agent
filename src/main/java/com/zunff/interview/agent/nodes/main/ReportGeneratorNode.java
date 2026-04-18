package com.zunff.interview.agent.nodes.main;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.config.PromptConfig;
import com.zunff.interview.model.dto.LevelMatchResult;
import com.zunff.interview.model.dto.llm.vars.ReportUserPromptVars;
import com.zunff.interview.model.entity.EvaluationRecord;
import com.zunff.interview.service.EvaluationRecordService;
import com.zunff.interview.service.InterviewSessionService;
import com.zunff.interview.service.extend.PromptTemplateService;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.websocket.InterviewWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 报告生成节点
 * 面试结束后生成综合评估报告，并通过 WebSocket 推送给前端
 */
@Slf4j
@Component
public class ReportGeneratorNode {

    private static final int MAX_ANSWER_CHARS = 8000;
    private static final int MAX_FOLLOW_UP_CHAIN_CHARS = 12000;

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;
    private final PromptConfig promptConfig;
    private final EvaluationRecordService evaluationRecordService;
    private final InterviewSessionService sessionService;
    private final InterviewWebSocketHandler webSocketHandler;

    public ReportGeneratorNode(
            ChatClient.Builder chatClientBuilder,
            PromptTemplateService promptTemplateService,
            PromptConfig promptConfig,
            EvaluationRecordService evaluationRecordService,
            InterviewSessionService sessionService,
            @Lazy InterviewWebSocketHandler webSocketHandler) {
        this.chatClientBuilder = chatClientBuilder;
        this.promptTemplateService = promptTemplateService;
        this.promptConfig = promptConfig;
        this.evaluationRecordService = evaluationRecordService;
        this.sessionService = sessionService;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 执行报告生成
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始生成面试报告");

        String sessionId = state.sessionId();
        String candidateProfile = state.candidateProfileAsText();

        String jobContext = state.hasJobAnalysisResult()
                ? state.jobAnalysisResult().generateJobSummary()
                : state.jobInfo();

        List<EvaluationRecord> evaluations = evaluationRecordService.getBySessionId(sessionId);

        int technicalQuestionsDone = state.technicalQuestionsDone();
        int businessQuestionsDone = state.businessQuestionsDone();
        double technicalAvgScore = state.technicalAverageScore();
        double businessAvgScore = state.businessAverageScore();

        double avgScore = 0;
        double avgAccuracy = 0, avgLogic = 0, avgFluency = 0, avgConfidence = 0;
        double avgEmotion = 0, avgBodyLanguage = 0, avgVoiceTone = 0;
        int totalQuestions = evaluations.size();

        if (totalQuestions > 0) {
            int totalScore = 0, totalAccuracy = 0, totalLogic = 0, totalFluency = 0, totalConfidence = 0;
            int totalEmotion = 0, totalBodyLanguage = 0, totalVoiceTone = 0;

            for (EvaluationRecord eval : evaluations) {
                if (eval.getOverallScore() != null) totalScore += eval.getOverallScore();
                if (eval.getAccuracy() != null) totalAccuracy += eval.getAccuracy();
                if (eval.getLogic() != null) totalLogic += eval.getLogic();
                if (eval.getFluency() != null) totalFluency += eval.getFluency();
                if (eval.getConfidence() != null) totalConfidence += eval.getConfidence();
                if (eval.getEmotionScore() != null) totalEmotion += eval.getEmotionScore();
                if (eval.getBodyLanguageScore() != null) totalBodyLanguage += eval.getBodyLanguageScore();
                if (eval.getVoiceToneScore() != null) totalVoiceTone += eval.getVoiceToneScore();
            }

            avgScore = (double) totalScore / totalQuestions;
            avgAccuracy = (double) totalAccuracy / totalQuestions;
            avgLogic = (double) totalLogic / totalQuestions;
            avgFluency = (double) totalFluency / totalQuestions;
            avgConfidence = (double) totalConfidence / totalQuestions;
            avgEmotion = (double) totalEmotion / totalQuestions;
            avgBodyLanguage = (double) totalBodyLanguage / totalQuestions;
            avgVoiceTone = (double) totalVoiceTone / totalQuestions;
        }

        String evalSummaryText = evaluations.isEmpty()
                ? "No evaluation records."
                : buildEvalSummary(evaluations);

        String technicalRoundScoresText = formatScoresList(state.technicalRoundScores());
        String businessRoundScoresText = formatScoresList(state.businessRoundScores());
        String levelMatchSummary = formatLevelMatchSummary(state);
        String followUpChainText = truncateChain(state.formatFollowUpChain());
        String interviewEndContext = buildInterviewEndContext(state);

        Map<String, Object> systemVars = new HashMap<>();
        systemVars.put("responseLanguage", promptConfig.getResponseLanguage());
        String systemPrompt = promptTemplateService.getPrompt("report-generator", systemVars);

        String userPrompt = promptTemplateService.getPrompt("report-generator-user",
                new ReportUserPromptVars(
                        promptConfig.getResponseLanguage(),
                        candidateProfile,
                        jobContext,
                        technicalQuestionsDone,
                        businessQuestionsDone,
                        technicalAvgScore,
                        businessAvgScore,
                        avgScore,
                        avgAccuracy,
                        avgLogic,
                        avgFluency,
                        avgConfidence,
                        avgEmotion,
                        avgBodyLanguage,
                        avgVoiceTone,
                        totalQuestions,
                        technicalRoundScoresText,
                        businessRoundScoresText,
                        levelMatchSummary,
                        followUpChainText,
                        interviewEndContext,
                        evalSummaryText
                ).asMap());

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String report = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            StringBuilder reportHeader = new StringBuilder();
            reportHeader.append("# 面试评估报告\n\n");
            reportHeader.append("> **综合得分**：").append(String.format("%.1f", avgScore)).append(" / 100\n");
            reportHeader.append("> **面试问题数**：").append(totalQuestions).append("\n\n");

            reportHeader.append("## 综合评估概览\n\n");
            reportHeader.append("### 内容维度\n");
            reportHeader.append("| 准确度 | 逻辑性 | 流畅度 | 自信度 |\n");
            reportHeader.append("|--------|--------|--------|--------|\n");
            reportHeader.append(String.format("| %.1f | %.1f | %.1f | %.1f |\n\n",
                    avgAccuracy, avgLogic, avgFluency, avgConfidence));

            reportHeader.append("### 多模态维度\n");
            reportHeader.append("| 表情表现 | 肢体语言 | 语音语调 |\n");
            reportHeader.append("|----------|----------|----------|\n");
            reportHeader.append(String.format("| %.1f | %.1f | %.1f |\n\n",
                    avgEmotion, avgBodyLanguage, avgVoiceTone));

            reportHeader.append("## 轮次表现\n\n");
            reportHeader.append("| 轮次 | 问题数 | 平均分 |\n");
            reportHeader.append("|------|--------|--------|\n");
            reportHeader.append(String.format("| 技术轮 | %d | %.1f |\n", technicalQuestionsDone, technicalAvgScore));
            reportHeader.append(String.format("| 业务轮 | %d | %.1f |\n", businessQuestionsDone, businessAvgScore));
            reportHeader.append(String.format("| **总计** | %d | %.1f |\n\n", totalQuestions, avgScore));

            reportHeader.append("---\n\n");

            String fullReport = reportHeader + report;

            sessionService.saveReport(sessionId, fullReport);

            webSocketHandler.sendFinalReport(sessionId, fullReport);

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.IS_FINISHED, true);
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("面试报告生成完成，平均得分: {}", avgScore);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("生成报告失败", e);
            Map<String, Object> updates = new HashMap<>();
            CircuitBreakerHelper.handleFailure(state, updates, e);
            updates.put(InterviewState.IS_FINISHED, true);
            return CompletableFuture.completedFuture(updates);
        }
    }

    private String buildEvalSummary(List<EvaluationRecord> evaluations) {
        StringBuilder evalSummary = new StringBuilder();
        for (int i = 0; i < evaluations.size(); i++) {
            EvaluationRecord eval = evaluations.get(i);
            int qNum = i + 1;
            evalSummary.append("\n### Question ").append(qNum).append("\n");
            evalSummary.append("- Question type: ").append(eval.getQuestionType() != null ? eval.getQuestionType() : "unknown").append("\n");
            evalSummary.append("- Difficulty: ").append(eval.getDifficulty() != null ? eval.getDifficulty() : "unknown").append("\n");
            if (!CollectionUtils.isEmpty(eval.getExpectedKeywords())) {
                evalSummary.append("- Expected keywords: ").append(String.join(", ", eval.getExpectedKeywords())).append("\n");
            }
            if (Boolean.TRUE.equals(eval.getModalityConcern())) {
                evalSummary.append("- Modality concern: yes\n");
            }
            evalSummary.append("- Question text: ").append(nullToEmpty(eval.getQuestion())).append("\n");
            evalSummary.append("- Answer: ").append(truncate(nullToEmpty(eval.getAnswer()), MAX_ANSWER_CHARS)).append("\n");
            evalSummary.append("- Overall score: ").append(eval.getOverallScore()).append("\n");
            evalSummary.append("  - Content: accuracy ").append(eval.getAccuracy());
            evalSummary.append(" | logic ").append(eval.getLogic());
            evalSummary.append(" | fluency ").append(eval.getFluency());
            evalSummary.append(" | confidence ").append(eval.getConfidence()).append("\n");
            evalSummary.append("  - Multimodal: emotion ").append(eval.getEmotionScore());
            evalSummary.append(" | body language ").append(eval.getBodyLanguageScore());
            evalSummary.append(" | voice tone ").append(eval.getVoiceToneScore()).append("\n");
            evalSummary.append("- Detailed evaluation: ").append(nullToEmpty(eval.getDetailedEvaluation())).append("\n");
            if (eval.getStrengths() != null && !eval.getStrengths().isEmpty()) {
                evalSummary.append("  - Strengths: ").append(String.join(", ", eval.getStrengths())).append("\n");
            }
            if (eval.getWeaknesses() != null && !eval.getWeaknesses().isEmpty()) {
                evalSummary.append("  - Weaknesses: ").append(String.join(", ", eval.getWeaknesses())).append("\n");
            }
        }
        return evalSummary.toString();
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "\n...[truncated]";
    }

    private static String formatScoresList(List<Integer> scores) {
        if (scores == null || scores.isEmpty()) {
            return "(none)";
        }
        return scores.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static String formatLevelMatchSummary(InterviewState state) {
        if (!state.hasLevelMatchResult()) {
            return "Not available";
        }
        LevelMatchResult lm = state.levelMatchResult();
        if (lm == null) {
            return "Not available";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Position level: ")
                .append(lm.positionLevel() != null ? lm.positionLevel().getDescription() : "unknown")
                .append("\n");
        sb.append("Candidate level: ")
                .append(lm.candidateLevel() != null ? lm.candidateLevel().getDescription() : "unknown")
                .append("\n");
        sb.append("Level mismatch: ").append(lm.isLevelMismatch()).append("\n");
        sb.append("Candidate lower than position: ").append(lm.isCandidateLower()).append("\n");
        sb.append("Difficulty range: ").append(lm.getDifficultyRange()).append("\n");
        if (lm.difficultyPreference() != null) {
            sb.append("Difficulty preference: ").append(lm.difficultyPreference()).append("\n");
        }
        return sb.toString().trim();
    }

    private static String buildInterviewEndContext(InterviewState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Eligible for early end (consecutive high scores): ").append(state.canEndInterviewEarly()).append("\n");
        sb.append("Consecutive high scores: ").append(state.consecutiveHighScores())
                .append(" / required ").append(state.consecutiveHighForEarlyEnd()).append("\n");
        sb.append("Technical round queue exhausted: ").append(state.isTechnicalRoundComplete()).append("\n");
        sb.append("Business round queue exhausted: ").append(state.isBusinessRoundComplete()).append("\n");
        sb.append("Technical questions answered (counter): ").append(state.technicalQuestionsDone()).append("\n");
        sb.append("Business questions answered (counter): ").append(state.businessQuestionsDone()).append("\n");
        return sb.toString().trim();
    }

    private static String truncateChain(String chain) {
        if (chain == null || chain.isEmpty()) {
            return "No follow-up history";
        }
        if (chain.length() <= MAX_FOLLOW_UP_CHAIN_CHARS) {
            return chain;
        }
        return chain.substring(0, MAX_FOLLOW_UP_CHAIN_CHARS) + "\n...[truncated]";
    }
}
