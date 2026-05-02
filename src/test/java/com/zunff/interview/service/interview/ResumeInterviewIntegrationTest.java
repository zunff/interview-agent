package com.zunff.interview.service.interview;

import com.zunff.interview.model.bo.GeneratedQuestion;
import com.zunff.interview.model.entity.GraphCheckpoint;
import com.zunff.interview.model.entity.InterviewSession;
import com.zunff.interview.model.websocket.QuestionMessage;
import com.zunff.interview.model.websocket.WebSocketMessage;
import com.zunff.interview.service.GraphCheckpointService;
import com.zunff.interview.service.InterviewSessionService;
import com.zunff.interview.websocket.InterviewWebSocketHandler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ResumeInterview 集成测试
 * 验证断线重连场景下从 checkpoint 恢复的正确性
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResumeInterviewIntegrationTest {

    @Autowired
    private InterviewBusinessService interviewBusinessService;

    @Autowired
    private InterviewSessionService sessionService;

    @Autowired
    private GraphCheckpointService checkpointService;

    @MockitoBean
    private InterviewWebSocketHandler webSocketHandler;

    private static final String TEST_SESSION_ID = "test-resume-session-001";

    @BeforeEach
    void setUp() {
        checkpointService.deleteByThreadId(TEST_SESSION_ID);
        InterviewSession existing = sessionService.getBySessionId(TEST_SESSION_ID);
        if (existing != null) {
            sessionService.removeById(existing.getId());
        }
    }

    private InterviewSession createTestSession() {
        InterviewSession session = InterviewSession.builder()
                .sessionId(TEST_SESSION_ID)
                .resume("测试简历")
                .jobInfo("Java开发")
                .maxTechnicalQuestions(6)
                .maxBusinessQuestions(4)
                .maxFollowUps(2)
                .currentQuestionIndex(0)
                .status(InterviewSession.Status.WAITING.name())
                .createTime(LocalDateTime.now())
                .build();
        sessionService.save(session);
        return session;
    }

    private void saveCheckpoint(String nodeId, String nextNodeId, Map<String, Object> state) {
        GraphCheckpoint checkpoint = GraphCheckpoint.builder()
                .checkpointId("test-checkpoint-" + System.nanoTime())
                .nodeId(nodeId)
                .nextNodeId(nextNodeId)
                .state(state)
                .build();
        checkpointService.saveOrUpdateCheckpoint(TEST_SESSION_ID, checkpoint);
    }

    /**
     * 构建与图节点一致的 state（存 GeneratedQuestion，不是 Map）
     */
    private Map<String, Object> buildAskQuestionState(String questionContent, String questionType, int questionIndex) {
        Map<String, Object> state = new HashMap<>();
        state.put("sessionId", TEST_SESSION_ID);
        state.put("currentRound", "TECHNICAL");

        GeneratedQuestion question = GeneratedQuestion.builder()
                .question(questionContent)
                .questionType(questionType)
                .questionIndex(questionIndex)
                .build();
        state.put("currentGeneratedQuestion", question);

        return state;
    }

    @Test
    @Order(1)
    void testResumeFromAskQuestionCheckpoint() {
        createTestSession();

        Map<String, Object> state = buildAskQuestionState(
                "请介绍一下 Spring Boot 的自动配置原理", "技术基础", 1);
        saveCheckpoint("technicalRound-tech_askQuestion", "", state);

        interviewBusinessService.resumeInterview(TEST_SESSION_ID);

        verify(webSocketHandler, times(1)).sendMessage(
                eq(TEST_SESSION_ID),
                argThat(msg -> msg.getType() == WebSocketMessage.Type.INTERVIEW_RESUMED)
        );
        verify(webSocketHandler, times(1)).sendQuestion(
                eq(TEST_SESSION_ID),
                any(QuestionMessage.class)
        );

        InterviewSession updated = sessionService.getBySessionId(TEST_SESSION_ID);
        assertEquals(InterviewSession.Status.IN_PROGRESS.name(), updated.getStatus());
    }

    @Test
    @Order(2)
    void testResumeFromBeforeProfileAnalysis() {
        createTestSession();

        Map<String, Object> state = new HashMap<>();
        state.put("sessionId", TEST_SESSION_ID);
        saveCheckpoint("__PARALLEL__(init)", "profileAnalysis", state);

        interviewBusinessService.resumeInterview(TEST_SESSION_ID);

        verify(webSocketHandler, times(1)).sendMessage(
                eq(TEST_SESSION_ID),
                argThat(msg -> msg.getType() == WebSocketMessage.Type.INTERVIEW_RESUMED)
        );

        InterviewSession updated = sessionService.getBySessionId(TEST_SESSION_ID);
        assertEquals(InterviewSession.Status.IN_PROGRESS.name(), updated.getStatus());
    }

    @Test
    @Order(3)
    void testResumeWithoutCheckpoint() {
        createTestSession();

        interviewBusinessService.resumeInterview(TEST_SESSION_ID);

        verify(webSocketHandler, times(1)).sendMessage(
                eq(TEST_SESSION_ID),
                argThat(msg -> msg.getType() == WebSocketMessage.Type.SELF_INTRO)
        );

        InterviewSession updated = sessionService.getBySessionId(TEST_SESSION_ID);
        assertEquals(InterviewSession.Status.IN_PROGRESS.name(), updated.getStatus());
    }

    @Test
    @Order(4)
    void testResumeWithUnknownNodeId() {
        createTestSession();

        Map<String, Object> state = new HashMap<>();
        state.put("sessionId", TEST_SESSION_ID);
        saveCheckpoint("UNKNOWN_NODE", "SOME_OTHER_NODE", state);

        interviewBusinessService.resumeInterview(TEST_SESSION_ID);

        verify(webSocketHandler, times(1)).sendMessage(
                eq(TEST_SESSION_ID),
                argThat(msg -> msg.getType() == WebSocketMessage.Type.SELF_INTRO)
        );

        InterviewSession updated = sessionService.getBySessionId(TEST_SESSION_ID);
        assertEquals(InterviewSession.Status.IN_PROGRESS.name(), updated.getStatus());
    }

    @Test
    @Order(5)
    void testRePushQuestionContent() {
        createTestSession();

        Map<String, Object> state = buildAskQuestionState("测试题目内容", "技术基础", 1);
        saveCheckpoint("technicalRound-tech_askQuestion", "", state);

        interviewBusinessService.resumeInterview(TEST_SESSION_ID);

        verify(webSocketHandler, times(1)).sendQuestion(
                eq(TEST_SESSION_ID),
                argThat(msg -> {
                    if (msg instanceof QuestionMessage qm) {
                        return "测试题目内容".equals(qm.getContent());
                    }
                    return false;
                })
        );
    }
}
