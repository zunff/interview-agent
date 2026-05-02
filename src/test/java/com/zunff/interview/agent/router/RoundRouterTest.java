package com.zunff.interview.agent.router;

import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.constant.InterviewRound;
import com.zunff.interview.constant.RouteDecision;
import com.zunff.interview.model.bo.FollowUpChainEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

/**
 * RoundRouter 单元测试
 * 测试路由保护逻辑：追问上限检查 + 质量未改善检查
 */
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoundRouterTest {

    @InjectMocks
    private RoundRouter roundRouter;

    private InterviewState state;

    @BeforeEach
    void setUp() {
        state = mock(InterviewState.class);
    }

    // ========== 测试场景1：追问上限保护 ==========

    @Test
    void testFollowUpAtMax_shouldRouteToNextQuestion() {
        // Given: 追问次数已达上限（3/3），决策为 followUp
        when(state.decision()).thenReturn(RouteDecision.FOLLOW_UP.getValue());
        when(state.followUpCount()).thenReturn(3);
        when(state.maxFollowUpsForCurrentRound()).thenReturn(3);
        when(state.followUpChain()).thenReturn(Collections.emptyList());
        when(state.currentRoundEnum()).thenReturn(InterviewRound.TECHNICAL);
        when(state.isTechnicalRound()).thenReturn(true);
        when(state.isTechnicalRoundComplete()).thenReturn(false);

        // When
        String result = roundRouter.route(state);

        // Then
        assertEquals(RouteDecision.NEXT_QUESTION.getValue(), result);
    }

    @Test
    void testDeepDiveAtMax_shouldRouteToNextQuestion() {
        // Given: 追问次数已达上限（3/3），决策为 deepDive
        when(state.decision()).thenReturn(RouteDecision.DEEP_DIVE.getValue());
        when(state.followUpCount()).thenReturn(3);
        when(state.maxFollowUpsForCurrentRound()).thenReturn(3);
        when(state.followUpChain()).thenReturn(Collections.emptyList());
        when(state.currentRoundEnum()).thenReturn(InterviewRound.TECHNICAL);
        when(state.isTechnicalRound()).thenReturn(true);
        when(state.isTechnicalRoundComplete()).thenReturn(false);

        // When
        String result = roundRouter.route(state);

        // Then
        assertEquals(RouteDecision.NEXT_QUESTION.getValue(), result);
    }

    // ========== 测试场景2：质量未改善保护 ==========

    @Test
    void testQualityNotImproved_shouldRouteToNextQuestion() {
        // Given: 追问质量未改善（68 -> 68），决策为 followUp
        when(state.decision()).thenReturn(RouteDecision.FOLLOW_UP.getValue());
        when(state.followUpCount()).thenReturn(2);
        when(state.maxFollowUpsForCurrentRound()).thenReturn(3);

        List<FollowUpChainEntity> chain = Arrays.asList(
                createFollowUpEntity(65),
                createFollowUpEntity(68)
        );
        when(state.followUpChain()).thenReturn(chain);
        when(state.currentRoundEnum()).thenReturn(InterviewRound.TECHNICAL);
        when(state.isTechnicalRound()).thenReturn(true);
        when(state.isTechnicalRoundComplete()).thenReturn(false);

        // When
        String result = roundRouter.route(state);

        // Then
        assertEquals(RouteDecision.NEXT_QUESTION.getValue(), result);
    }

    @Test
    void testQualityDegraded_shouldRouteToNextQuestion() {
        // Given: 追问质量下降（75 -> 70），决策为 deepDive
        when(state.decision()).thenReturn(RouteDecision.DEEP_DIVE.getValue());
        when(state.followUpCount()).thenReturn(2);
        when(state.maxFollowUpsForCurrentRound()).thenReturn(3);

        List<FollowUpChainEntity> chain = Arrays.asList(
                createFollowUpEntity(75),
                createFollowUpEntity(70)
        );
        when(state.followUpChain()).thenReturn(chain);
        when(state.currentRoundEnum()).thenReturn(InterviewRound.TECHNICAL);
        when(state.isTechnicalRound()).thenReturn(true);
        when(state.isTechnicalRoundComplete()).thenReturn(false);

        // When
        String result = roundRouter.route(state);

        // Then
        assertEquals(RouteDecision.NEXT_QUESTION.getValue(), result);
    }

    // ========== 测试场景3：质量有改善，正常追问 ==========

    @Test
    void testQualityImproved_shouldAllowFollowUp() {
        // Given: 追问质量有改善（65 -> 70），决策为 followUp
        when(state.decision()).thenReturn(RouteDecision.FOLLOW_UP.getValue());
        when(state.followUpCount()).thenReturn(2);
        when(state.maxFollowUpsForCurrentRound()).thenReturn(3);

        List<FollowUpChainEntity> chain = Arrays.asList(
                createFollowUpEntity(65),
                createFollowUpEntity(70)
        );
        when(state.followUpChain()).thenReturn(chain);
        when(state.currentRoundEnum()).thenReturn(InterviewRound.TECHNICAL);
        when(state.isTechnicalRound()).thenReturn(true);
        when(state.isTechnicalRoundComplete()).thenReturn(false);

        // When
        String result = roundRouter.route(state);

        // Then
        assertEquals(RouteDecision.FOLLOW_UP.getValue(), result);
    }

    // ========== 测试场景4：正常追问 ==========

    @Test
    void testNormalDeepDive_shouldRouteToDeepDive() {
        // Given: 正常 deepDive 决策
        when(state.decision()).thenReturn(RouteDecision.DEEP_DIVE.getValue());
        when(state.followUpCount()).thenReturn(1);
        when(state.maxFollowUpsForCurrentRound()).thenReturn(3);
        when(state.followUpChain()).thenReturn(Collections.singletonList(createFollowUpEntity(65)));
        when(state.currentRoundEnum()).thenReturn(InterviewRound.TECHNICAL);

        // When
        String result = roundRouter.route(state);

        // Then
        assertEquals(RouteDecision.DEEP_DIVE.getValue(), result);
    }

    @Test
    void testNormalChallengeMode_shouldRouteToChallengeMode() {
        // Given: 正常 challengeMode 决策
        when(state.decision()).thenReturn(RouteDecision.CHALLENGE_MODE.getValue());
        when(state.followUpCount()).thenReturn(1);
        when(state.maxFollowUpsForCurrentRound()).thenReturn(3);
        when(state.followUpChain()).thenReturn(Collections.singletonList(createFollowUpEntity(85)));
        when(state.currentRoundEnum()).thenReturn(InterviewRound.TECHNICAL);

        // When
        String result = roundRouter.route(state);

        // Then
        assertEquals(RouteDecision.CHALLENGE_MODE.getValue(), result);
    }

    // ========== 测试场景5：轮次完成检查 ==========

    @Test
    void testNextQuestionRoundComplete_shouldRouteToRoundComplete() {
        // Given: nextQuestion 且轮次已完成
        when(state.decision()).thenReturn(RouteDecision.NEXT_QUESTION.getValue());
        when(state.followUpCount()).thenReturn(0);
        when(state.maxFollowUpsForCurrentRound()).thenReturn(3);
        when(state.currentRoundEnum()).thenReturn(InterviewRound.TECHNICAL);
        when(state.isTechnicalRound()).thenReturn(true);
        when(state.isTechnicalRoundComplete()).thenReturn(true);

        // When
        String result = roundRouter.route(state);

        // Then
        assertEquals(RouteDecision.ROUND_COMPLETE.getValue(), result);
    }

    @Test
    void testNextQuestionRoundNotComplete_shouldRouteToNextQuestion() {
        // Given: nextQuestion 但轮次未完成
        when(state.decision()).thenReturn(RouteDecision.NEXT_QUESTION.getValue());
        when(state.currentRoundEnum()).thenReturn(InterviewRound.TECHNICAL);
        when(state.isTechnicalRound()).thenReturn(true);
        when(state.isTechnicalRoundComplete()).thenReturn(false);

        // When
        String result = roundRouter.route(state);

        // Then
        assertEquals(RouteDecision.NEXT_QUESTION.getValue(), result);
    }

    // ========== 辅助方法 ==========

    private FollowUpChainEntity createFollowUpEntity(int score) {
        return FollowUpChainEntity.builder()
                .overallScore(score)
                .followUpQuestion("Test follow-up")
                .detailedEvaluation("Test evaluation")
                .build();
    }
}
