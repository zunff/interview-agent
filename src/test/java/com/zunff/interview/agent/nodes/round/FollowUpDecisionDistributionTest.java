package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.bo.GeneratedQuestion;
import com.zunff.interview.service.extend.MultimodalAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 追问类型分布统计测试
 * 模拟大量随机场景，验证各追问类型的分布是否符合预期
 */
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowUpDecisionDistributionTest {

    @Mock
    private MultimodalAnalysisService multimodalAnalysisService;

    @InjectMocks
    private FollowUpDecisionNode followUpDecisionNode;

    @Test
    void testDistribution_100RandomScenarios() {
        // Given: 模拟 100 个随机场景
        int totalScenarios = 100;
        Map<String, Integer> decisionCount = new HashMap<>();
        Random random = new Random(42); // 固定种子，结果可复现

        // Mock LLM 决策（模拟更真实的 LLM 行为）
        when(multimodalAnalysisService.decideFollowUpRoute(
                any(EvaluationBO.class),
                any(GeneratedQuestion.class),
                any(Integer.class),
                any(Integer.class),
                any()
        )).thenAnswer(invocation -> {
            EvaluationBO eval = invocation.getArgument(0, EvaluationBO.class);
            int score = eval.getOverallScore();
            boolean hasWeakness = eval.getWeaknesses() != null && !eval.getWeaknesses().isEmpty();
            boolean concern = eval.isModalityConcern();
            int remaining = invocation.getArgument(3, Integer.class) - invocation.getArgument(2, Integer.class);

            // 模拟 LLM 的精细化决策
            if (remaining <= 1 && !hasWeakness) return "nextQuestion";
            if (score < 55 && hasWeakness) return "deepDive";
            if (score > 80 && !hasWeakness) return "challengeMode";
            if (score > 75 && !hasWeakness) return "nextQuestion";
            if (concern) return "followUp";
            if (score >= 65 && hasWeakness) return "followUp";
            if (score < 65 && hasWeakness) return "deepDive";
            return "nextQuestion";
        });

        // When: 执行 100 次随机场景
        for (int i = 0; i < totalScenarios; i++) {
            // 随机生成分数（40-95 分）
            int score = 40 + random.nextInt(56); // 40-95
            // 随机是否有弱点（60% 概率有弱点）
            boolean hasWeakness = random.nextDouble() < 0.6;
            // 随机是否有模态异常（20% 概率）
            boolean concern = random.nextDouble() < 0.2;
            // 随机追问次数（0-2）
            int followUpCount = random.nextInt(3);
            // 随机最大追问次数（2-3）
            int maxFollowUps = 2 + random.nextInt(2);

            InterviewState state = createMockState(followUpCount, maxFollowUps, score, hasWeakness, concern);
            EvaluationBO evaluation = createEvaluation(score, hasWeakness, concern);
            when(state.getCurrentEvaluation()).thenReturn(evaluation);
            when(state.getCurrentGeneratedQuestion()).thenReturn(createQuestion("medium"));
            when(state.followUpChain()).thenReturn(Collections.emptyList());

            try {
                CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);
                Map<String, Object> updates = result.join();
                String decision = (String) updates.get(InterviewState.DECISION);

                decisionCount.merge(decision, 1, Integer::sum);
            } catch (Exception e) {
                System.err.println("Error in scenario " + i + ": " + e.getMessage());
            }
        }

        // Then: 验证分布合理性
        System.out.println("\n========== 追问类型分布统计 ==========");
        decisionCount.forEach((decision, count) -> {
            double percentage = count * 100.0 / totalScenarios;
            System.out.printf("%-15s: %2d 次 (%.1f%%)%n", decision, count, percentage);
        });
        System.out.println("=====================================\n");

        int nextQuestionCount = decisionCount.getOrDefault(RouteDecision.NEXT_QUESTION.getValue(), 0);
        int deepDiveCount = decisionCount.getOrDefault(RouteDecision.DEEP_DIVE.getValue(), 0);
        int challengeModeCount = decisionCount.getOrDefault(RouteDecision.CHALLENGE_MODE.getValue(), 0);
        int followUpCount = decisionCount.getOrDefault(RouteDecision.FOLLOW_UP.getValue(), 0);

        // 验证 nextQuestion 占比（预期 20-50%）
        double nextQuestionPercent = nextQuestionCount * 100.0 / totalScenarios;
        System.out.printf("nextQuestion: %.1f%% (预期 20-50%%)%n", nextQuestionPercent);
        assertTrue(nextQuestionPercent >= 15, "nextQuestion 占比过低: " + nextQuestionPercent + "%");

        // 验证 deepDive 占比（预期 10-30%）
        double deepDivePercent = deepDiveCount * 100.0 / totalScenarios;
        System.out.printf("deepDive: %.1f%% (预期 10-30%%)%n", deepDivePercent);
        assertTrue(deepDivePercent >= 5, "deepDive 占比过低: " + deepDivePercent + "%");

        // 验证 challengeMode 占比（预期 5-25%）
        double challengeModePercent = challengeModeCount * 100.0 / totalScenarios;
        System.out.printf("challengeMode: %.1f%% (预期 5-25%%)%n", challengeModePercent);
        assertTrue(challengeModePercent >= 3, "challengeMode 占比过低: " + challengeModePercent + "%");

        // 验证 followUp 占比（预期 20-50%）
        double followUpPercent = followUpCount * 100.0 / totalScenarios;
        System.out.printf("followUp: %.1f%% (预期 20-50%%)%n", followUpPercent);
        assertTrue(followUpPercent >= 10, "followUp 占比过低: " + followUpPercent + "%");

        // 验证不再是单一 followUp 主导
        assertFalse(
                followUpPercent > 60,
                "followUp 占比过高（" + followUpPercent + "%），仍然主导分布"
        );
    }

    @Test
    void testSpecificScenario_LowScoreWithWeakness() {
        // Given: 低分（40-59）且有弱点的场景，应该全部返回 DEEP_DIVE
        int deepDiveCount = 0;
        int totalTests = 20;

        when(multimodalAnalysisService.decideFollowUpRoute(
                any(EvaluationBO.class),
                any(GeneratedQuestion.class),
                eq(0),
                eq(3),
                any()
        )).thenReturn("deepDive");

        for (int score = 40; score < 60; score++) {
            InterviewState state = createMockState(0, 3, score, true, false);
            EvaluationBO evaluation = createEvaluation(score, true, false);
            when(state.getCurrentEvaluation()).thenReturn(evaluation);
            when(state.getCurrentGeneratedQuestion()).thenReturn(createQuestion("medium"));
            when(state.followUpChain()).thenReturn(Collections.emptyList());

            CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);
            Map<String, Object> updates = result.join();
            String decision = (String) updates.get(InterviewState.DECISION);

            if (RouteDecision.DEEP_DIVE.getValue().equals(decision)) {
                deepDiveCount++;
            }
        }

        // Then: 所有低分有弱点的场景应该返回 DEEP_DIVE
        System.out.printf("低分有弱点场景: %d/%d 返回 DEEP_DIVE%n", deepDiveCount, totalTests);
        assertTrue(deepDiveCount >= 15, "低分有弱点应该大部分返回 DEEP_DIVE");
    }

    @Test
    void testSpecificScenario_HighScoreNoWeakness() {
        // Given: 高分（76-95）且无弱点的场景，应该返回 CHALLENGE_MODE 或 NEXT_QUESTION
        int challengeOrNextCount = 0;
        int totalTests = 20;

        for (int score = 76; score <= 95; score++) {
            InterviewState state = createMockState(0, 3, score, false, false);
            EvaluationBO evaluation = createEvaluation(score, false, false);
            when(state.getCurrentEvaluation()).thenReturn(evaluation);
            when(state.getCurrentGeneratedQuestion()).thenReturn(createQuestion("medium"));
            when(state.followUpChain()).thenReturn(Collections.emptyList());

            CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);
            Map<String, Object> updates = result.join();
            String decision = (String) updates.get(InterviewState.DECISION);

            if (RouteDecision.CHALLENGE_MODE.getValue().equals(decision) ||
                    RouteDecision.NEXT_QUESTION.getValue().equals(decision)) {
                challengeOrNextCount++;
            }
        }

        // Then: 高分无弱点的场景应该返回 CHALLENGE_MODE 或 NEXT_QUESTION
        System.out.printf("高分无弱点场景: %d/%d 返回 CHALLENGE_MODE 或 NEXT_QUESTION%n",
                challengeOrNextCount, totalTests);
        assertTrue(challengeOrNextCount >= 15, "高分无弱点应该大部分返回 CHALLENGE_MODE 或 NEXT_QUESTION");
    }

    // ========== 辅助方法 ==========

    private InterviewState createMockState(int followUpCount, int maxFollowUps,
                                            int score, boolean hasWeakness, boolean concern) {
        InterviewState state = org.mockito.Mockito.mock(InterviewState.class);
        when(state.followUpCount()).thenReturn(followUpCount);
        when(state.maxFollowUpsForCurrentRound()).thenReturn(maxFollowUps);
        when(state.followUpChain()).thenReturn(Collections.emptyList());
        return state;
    }

    private EvaluationBO createEvaluation(int score, boolean hasWeakness, boolean concern) {
        EvaluationBO.EvaluationBOBuilder builder = EvaluationBO.builder()
                .overallScore(score)
                .accuracy(score)
                .logic(score)
                .fluency(score)
                .confidence(score)
                .modalityConcern(concern);

        if (hasWeakness) {
            builder.weaknesses(Arrays.asList("logic gap", "accuracy issue"));
        } else {
            builder.weaknesses(Collections.emptyList());
        }

        return builder.build();
    }

    private GeneratedQuestion createQuestion(String difficulty) {
        return GeneratedQuestion.builder()
                .question("Test question?")
                .difficulty(difficulty)
                .expectedKeywords(Arrays.asList("keyword1", "keyword2"))
                .reason("Test intent")
                .build();
    }
}
