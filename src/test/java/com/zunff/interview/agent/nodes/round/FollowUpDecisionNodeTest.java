package com.zunff.interview.agent.nodes.round;

import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.constant.Difficulty;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.bo.FollowUpChainEntity;
import com.zunff.interview.model.bo.GeneratedQuestion;
import com.zunff.interview.service.extend.MultimodalAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * FollowUpDecisionNode 单元测试
 * 测试优化后的规则预判逻辑
 */
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowUpDecisionNodeTest {

    @Mock
    private MultimodalAnalysisService multimodalAnalysisService;

    @InjectMocks
    private FollowUpDecisionNode followUpDecisionNode;

    private InterviewState state;
    private EvaluationBO evaluation;
    private GeneratedQuestion question;

    @BeforeEach
    void setUp() {
        state = createMockState(0, 3);
        evaluation = createEvaluation(70, true, false);
        question = createQuestion("medium");
    }

    // ========== 测试场景1：已达追问上限 ==========

    @Test
    void testMaxFollowUpReached_shouldReturnNextQuestion() {
        // Given: 已达追问上限（3/3）
        state = createMockState(3, 3);
        evaluation = createEvaluation(60, true, false);

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);

        // Then
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals(RouteDecision.NEXT_QUESTION.getValue(), decision);
    }

    // ========== 测试场景2：低分DEEP_DIVE（<60）==========

    @Test
    void testLowScoreDeepDive_shouldReturnDeepDive() {
        // Given: 低分（55分）且有弱点，剩余次数>=2
        evaluation = createEvaluation(55, true, false);
        state = createMockState(0, 3);

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);

        // Then
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals(RouteDecision.DEEP_DIVE.getValue(), decision);
    }

    @Test
    void testLowScoreBoundaryDeepDive_shouldReturnDeepDive() {
        // Given: 低分边界（59分）且有弱点
        evaluation = createEvaluation(59, true, false);
        state = createMockState(0, 3);

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);

        // Then
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals(RouteDecision.DEEP_DIVE.getValue(), decision);
    }

    @Test
    void testLowScoreNoWeakness_shouldNotReturnDeepDive() {
        // Given: 低分（55分）但无弱点
        evaluation = createEvaluation(55, false, false);
        state = createMockState(0, 3);

        // When & Then: 应该交给 LLM 或其他决策
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertNotEquals(RouteDecision.DEEP_DIVE.getValue(), decision);
    }

    // ========== 测试场景3：高分CHALLENGE_MODE（>75）==========

    @Test
    void testHighScoreChallengeMode_shouldReturnChallengeMode() {
        // Given: 高分（80分）且无弱点，剩余次数>=1
        evaluation = createEvaluation(80, false, false);
        state = createMockState(0, 3);

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);

        // Then
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals(RouteDecision.CHALLENGE_MODE.getValue(), decision);
    }

    @Test
    void testHighScoreBoundaryChallengeMode_shouldReturnChallengeMode() {
        // Given: 高分边界（76分）且无弱点
        evaluation = createEvaluation(76, false, false);
        state = createMockState(0, 3);

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals(RouteDecision.CHALLENGE_MODE.getValue(), decision);
    }

    @Test
    void testHighScoreWithWeakness_shouldNotReturnChallengeMode() {
        // Given: 高分（80分）但有弱点
        evaluation = createEvaluation(80, true, false);
        state = createMockState(0, 3);

        // When & Then: 应该交给 LLM 或其他决策
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertNotEquals(RouteDecision.CHALLENGE_MODE.getValue(), decision);
    }

    // ========== 测试场景4：表现优秀（>=80）且无弱点 ==========

    @Test
    void testExcellentScoreNoWeakness_shouldReturnChallengeMode() {
        // Given: 优秀分数（85分）且无弱点，会先触发 CHALLENGE_MODE（score > 75）
        evaluation = createEvaluation(85, false, false);
        state = createMockState(0, 3);

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);

        // Then: 85分 > 75，应该返回 CHALLENGE_MODE
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals(RouteDecision.CHALLENGE_MODE.getValue(), decision);
    }

    @Test
    void testExcellentScoreNoWeaknessNoRemaining_shouldReturnNextQuestion() {
        // Given: 优秀分数（85分）且无弱点，但无剩余追问次数
        evaluation = createEvaluation(85, false, false);
        state = createMockState(3, 3); // used=3, max=3

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);

        // Then: 已达上限，应该返回 NEXT_QUESTION
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals(RouteDecision.NEXT_QUESTION.getValue(), decision);
    }

    // ========== 测试场景5：追问质量未改善 ==========

    @Test
    void testQualityNotImproved_shouldReturnNextQuestion() {
        // Given: 连续追问质量未改善
        evaluation = createEvaluation(70, true, false);
        state = createMockState(2, 3);
        // 添加追问历史：分数未提升（68 -> 68）
        List<FollowUpChainEntity> chain = Arrays.asList(
                createFollowUpEntity(68, "logic gap"),
                createFollowUpEntity(68, "logic gap")
        );
        when(state.followUpChain()).thenReturn(chain);

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);

        // Then
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals(RouteDecision.NEXT_QUESTION.getValue(), decision);
    }

    @Test
    void testQualityImproved_shouldContinueFollowUp() {
        // Given: 追问质量有改善
        evaluation = createEvaluation(75, true, false);
        state = createMockState(2, 3);
        // 添加追问历史：分数提升（65 -> 70）
        List<FollowUpChainEntity> chain = Arrays.asList(
                createFollowUpEntity(65, "logic gap"),
                createFollowUpEntity(70, "logic gap")
        );
        when(state.followUpChain()).thenReturn(chain);

        // Mock LLM 决策（因为会进入 LLM 决策）
        when(multimodalAnalysisService.decideFollowUpRoute(
                any(EvaluationBO.class),
                any(GeneratedQuestion.class),
                eq(2),
                eq(3),
                any()
        )).thenReturn("followUp");

        // When & Then: 应该交给 LLM 决策，返回 followUp
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals("followUp", decision);
    }

    // ========== 测试场景6：仅剩1次追问 ==========

    @Test
    void testRemainingOneWithConcern_shouldReturnFollowUp() {
        // Given: 仅剩1次追问且有模态异常
        evaluation = createEvaluation(65, true, true);
        state = createMockState(2, 3);

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);

        // Then
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals(RouteDecision.FOLLOW_UP.getValue(), decision);
    }

    @Test
    void testRemainingOneNoWeakness_shouldReturnNextQuestion() {
        // Given: 仅剩1次追问，分数>=70且无弱点
        evaluation = createEvaluation(75, false, false);
        state = createMockState(2, 3);

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);

        // Then
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals(RouteDecision.NEXT_QUESTION.getValue(), decision);
    }

    // ========== 测试场景7：中等分数有弱点（LLM决策）==========

    @Test
    void testMidScoreWithWeakness_shouldDelegateToLLM() {
        // Given: 中等分数（70分）且有弱点，无模态异常
        evaluation = createEvaluation(70, true, false);
        state = createMockState(0, 3);

        // Mock LLM 决策
        when(multimodalAnalysisService.decideFollowUpRoute(
                any(EvaluationBO.class),
                any(GeneratedQuestion.class),
                eq(0),
                eq(3),
                any()
        )).thenReturn("followUp");

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);

        // Then
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals("followUp", decision);
    }

    // ========== 测试场景8：有弱点无模态异常 ==========

    @Test
    void testWeaknessNoConcern_shouldDelegateToLLM() {
        // Given: 有弱点，无模态异常，分数=60（在 60-75 范围内）
        evaluation = createEvaluation(60, true, false);
        state = createMockState(0, 3);

        // Mock LLM 决策
        when(multimodalAnalysisService.decideFollowUpRoute(
                any(EvaluationBO.class),
                any(GeneratedQuestion.class),
                eq(0),
                eq(3),
                any()
        )).thenReturn("followUp");

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);

        // Then: 应该交给 LLM 决策
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals("followUp", decision);
    }

    @Test
    void testScore59WithWeakness_shouldReturnDeepDive() {
        // Given: 分数=59（<60），有弱点
        evaluation = createEvaluation(59, true, false);
        state = createMockState(0, 3);

        // When
        CompletableFuture<Map<String, Object>> result = followUpDecisionNode.execute(state);

        // Then: 应该返回 DEEP_DIVE
        Map<String, Object> updates = result.join();
        String decision = (String) updates.get(InterviewState.DECISION);
        assertEquals(RouteDecision.DEEP_DIVE.getValue(), decision);
    }

    // ========== 辅助方法 ==========

    private InterviewState createMockState(int followUpCount, int maxFollowUps) {
        InterviewState state = org.mockito.Mockito.mock(InterviewState.class);
        when(state.followUpCount()).thenReturn(followUpCount);
        when(state.maxFollowUpsForCurrentRound()).thenReturn(maxFollowUps);
        when(state.followUpChain()).thenReturn(Collections.emptyList());
        when(state.getCurrentEvaluation()).thenReturn(evaluation);
        when(state.getCurrentGeneratedQuestion()).thenReturn(question);
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

    private FollowUpChainEntity createFollowUpEntity(int score, String... weaknesses) {
        return FollowUpChainEntity.builder()
                .overallScore(score)
                .followUpQuestion("Test follow-up")
                .detailedEvaluation("Test evaluation")
                .build();
    }
}
