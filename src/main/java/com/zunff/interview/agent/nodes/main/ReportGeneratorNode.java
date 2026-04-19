package com.zunff.interview.agent.nodes.main;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.config.PromptConfig;
import com.zunff.interview.model.bo.InterviewQuestionBO;
import com.zunff.interview.model.bo.LevelMatchResult;
import com.zunff.interview.model.dto.llm.vars.ReportUserPromptVars;
import com.zunff.interview.service.InterviewSessionService;
import com.zunff.interview.service.extend.PromptTemplateService;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.websocket.InterviewWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
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
    private final InterviewSessionService sessionService;
    private final InterviewWebSocketHandler webSocketHandler;

    public ReportGeneratorNode(
            ChatClient.Builder chatClientBuilder,
            PromptTemplateService promptTemplateService,
            PromptConfig promptConfig,
            InterviewSessionService sessionService,
            @Lazy InterviewWebSocketHandler webSocketHandler) {
        this.chatClientBuilder = chatClientBuilder;
        this.promptTemplateService = promptTemplateService;
        this.promptConfig = promptConfig;
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

        // 从新的列表获取已评估的题目
        List<InterviewQuestionBO> technicalQuestions = state.evaluatedTechnicalQuestions();
        List<InterviewQuestionBO> businessQuestions = state.evaluatedBusinessQuestions();

        int technicalQuestionsDone = state.technicalQuestionsDone();
        int businessQuestionsDone = state.businessQuestionsDone();
        double technicalAvgScore = calculateAverageScore(technicalQuestions);
        double businessAvgScore = calculateAverageScore(businessQuestions);

        // 计算总体统计
        List<InterviewQuestionBO> allQuestions = new ArrayList<>();
        allQuestions.addAll(technicalQuestions);
        allQuestions.addAll(businessQuestions);

        double avgScore = 0;
        double avgAccuracy = 0, avgLogic = 0, avgFluency = 0, avgConfidence = 0;
        double avgEmotion = 0, avgBodyLanguage = 0, avgVoiceTone = 0;
        int totalQuestions = allQuestions.size();

        if (totalQuestions > 0) {
            int totalScore = 0, totalAccuracy = 0, totalLogic = 0, totalFluency = 0, totalConfidence = 0;
            int totalEmotion = 0, totalBodyLanguage = 0, totalVoiceTone = 0;

            for (InterviewQuestionBO q : allQuestions) {
                if (q.getOverallScore() != null) totalScore += q.getOverallScore();
                if (q.getAccuracy() != null) totalAccuracy += q.getAccuracy();
                if (q.getLogic() != null) totalLogic += q.getLogic();
                if (q.getFluency() != null) totalFluency += q.getFluency();
                if (q.getConfidence() != null) totalConfidence += q.getConfidence();
                if (q.getEmotionScore() != null) totalEmotion += q.getEmotionScore();
                if (q.getBodyLanguageScore() != null) totalBodyLanguage += q.getBodyLanguageScore();
                if (q.getVoiceToneScore() != null) totalVoiceTone += q.getVoiceToneScore();
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

        String evalSummaryText = allQuestions.isEmpty()
                ? "No evaluation records."
                : buildEvalSummary(allQuestions);

        String technicalRoundScoresText = formatScoresList(extractScores(technicalQuestions));
        String businessRoundScoresText = formatScoresList(extractScores(businessQuestions));
        String levelMatchSummary = formatLevelMatchSummary(state);
        String followUpChainText = truncateChain(state.formatFollowUpChain());
        String interviewEndContext = buildInterviewEndContext(state);

        Map<String, Object> systemVars = new HashMap<>();
        systemVars.put("responseLanguage", promptConfig.getResponseLanguage());
        String systemPrompt = promptTemplateService.getPrompt("report-generator", systemVars);

        String userPrompt = promptTemplateService.getPrompt("report-generator-user",
                ReportUserPromptVars.builder()
                        .responseLanguage(promptConfig.getResponseLanguage())
                        .candidateProfile(candidateProfile)
                        .jobInfo(jobContext)
                        .technicalQuestionsDone(technicalQuestionsDone)
                        .businessQuestionsDone(businessQuestionsDone)
                        .technicalAvgScore(technicalAvgScore)
                        .businessAvgScore(businessAvgScore)
                        .avgScore(avgScore)
                        .avgAccuracy(avgAccuracy)
                        .avgLogic(avgLogic)
                        .avgFluency(avgFluency)
                        .avgConfidence(avgConfidence)
                        .avgEmotion(avgEmotion)
                        .avgBodyLanguage(avgBodyLanguage)
                        .avgVoiceTone(avgVoiceTone)
                        .totalQuestions(totalQuestions)
                        .technicalRoundScoresText(technicalRoundScoresText)
                        .businessRoundScoresText(businessRoundScoresText)
                        .levelMatchSummary(levelMatchSummary)
                        .followUpChainText(followUpChainText)
                        .interviewEndContext(interviewEndContext)
                        .evalSummary(evalSummaryText)
                        .build()
                        .asMap());

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

    private String buildEvalSummary(List<InterviewQuestionBO> questions) {
        StringBuilder evalSummary = new StringBuilder();

        int mainQNum = 0;
        for (InterviewQuestionBO q : questions) {
            if (!q.isFollowUp()) {
                mainQNum++;
                // 主问题：展示标准答案、建议、评估结果
                evalSummary.append("\n### Question ").append(mainQNum).append("\n");
                evalSummary.append("- Question type: ").append(q.getQuestionType()).append("\n");
                evalSummary.append("- Difficulty: ").append(q.getDifficulty()).append("\n");

                if (q.getStandardAnswer() != null && !q.getStandardAnswer().isEmpty()) {
                    evalSummary.append("- Standard Answer: ").append(q.getStandardAnswer()).append("\n");
                }
                if (q.getSuggestions() != null && !q.getSuggestions().isEmpty()) {
                    evalSummary.append("- Suggestions: ").append(q.getSuggestions()).append("\n");
                }
                if (!CollectionUtils.isEmpty(q.getExpectedKeywords())) {
                    evalSummary.append("- Expected keywords: ").append(String.join(", ", q.getExpectedKeywords())).append("\n");
                }
                if (Boolean.TRUE.equals(q.getModalityConcern())) {
                    evalSummary.append("- Modality concern: yes\n");
                }

                evalSummary.append("- Question: ").append(nullToEmpty(q.getQuestion())).append("\n");
                evalSummary.append("- Answer: ").append(truncate(nullToEmpty(q.getAnswer()), MAX_ANSWER_CHARS)).append("\n");
                evalSummary.append("- Overall score: ").append(q.getOverallScore()).append("\n");
                evalSummary.append("  - Content: accuracy ").append(q.getAccuracy());
                evalSummary.append(" | logic ").append(q.getLogic());
                evalSummary.append(" | fluency ").append(q.getFluency());
                evalSummary.append(" | confidence ").append(q.getConfidence()).append("\n");
                evalSummary.append("  - Multimodal: emotion ").append(q.getEmotionScore());
                evalSummary.append(" | body language ").append(q.getBodyLanguageScore());
                evalSummary.append(" | voice tone ").append(q.getVoiceToneScore()).append("\n");
                evalSummary.append("- Detailed evaluation: ").append(nullToEmpty(q.getDetailedEvaluation())).append("\n");
                if (!CollectionUtils.isEmpty(q.getStrengths())) {
                    evalSummary.append("  - Strengths: ").append(String.join(", ", q.getStrengths())).append("\n");
                }
                if (!CollectionUtils.isEmpty(q.getWeaknesses())) {
                    evalSummary.append("  - Weaknesses: ").append(String.join(", ", q.getWeaknesses())).append("\n");
                }
            } else {
                // 追问：挂在当前主问题下
                evalSummary.append("  - Follow-up: ").append(q.getQuestion()).append("\n");
                evalSummary.append("    - Score: ").append(q.getOverallScore()).append("\n");
                evalSummary.append("    - Answer: ").append(truncate(nullToEmpty(q.getAnswer()), MAX_ANSWER_CHARS / 2)).append("\n");
            }
        }

        return evalSummary.toString();
    }

    private double calculateAverageScore(List<InterviewQuestionBO> questions) {
        if (questions.isEmpty()) return 0;
        return questions.stream()
                .filter(q -> q.getOverallScore() != null)
                .mapToInt(InterviewQuestionBO::getOverallScore)
                .average()
                .orElse(0);
    }

    private List<Integer> extractScores(List<InterviewQuestionBO> questions) {
        List<Integer> scores = new ArrayList<>();
        for (InterviewQuestionBO q : questions) {
            if (!q.isFollowUp() && q.getOverallScore() != null) {
                scores.add(q.getOverallScore());
            }
        }
        return scores;
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
