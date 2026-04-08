package com.zunff.agent.service;

import com.zunff.agent.model.dto.request.StartInterviewRequest;
import com.zunff.agent.model.dto.request.SubmitAnswerRequest;
import com.zunff.agent.model.dto.response.InterviewAnswerResponse;
import com.zunff.agent.model.dto.response.InterviewStartResponse;
import com.zunff.agent.model.dto.response.ReportResponse;
import com.zunff.agent.model.dto.response.SessionResponse;
import com.zunff.agent.model.dto.websocket.QuestionMessage;
import com.zunff.agent.model.entity.InterviewSessionEntity;
import com.zunff.agent.state.InterviewState;
import com.zunff.agent.websocket.InterviewWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 面试业务服务
 * 封装面试相关的所有业务逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewBusinessService {

    private final CompiledGraph<InterviewState> interviewAgent;
    private final InterviewSessionService sessionService;
    private final InterviewWebSocketHandler webSocketHandler;

    /**
     * 开始面试
     */
    public InterviewStartResponse startInterview(StartInterviewRequest request) {
        // 创建会话
        var session = sessionService.createSession(
                request.getResume(),
                request.getJobInfo(),
                request.getInterviewType(),
                request.getMaxQuestions(),
                request.getMaxFollowUps()
        );

        // 初始化状态
        Map<String, Object> initialState = new HashMap<>();
        initialState.put(InterviewState.SESSION_ID, session.getSessionId());
        initialState.put(InterviewState.RESUME, request.getResume());
        initialState.put(InterviewState.JOB_INFO, request.getJobInfo());
        initialState.put(InterviewState.INTERVIEW_TYPE, request.getInterviewType());
        initialState.put(InterviewState.MAX_QUESTIONS, request.getMaxQuestions());
        initialState.put(InterviewState.MAX_FOLLOW_UPS, request.getMaxFollowUps());

        RunnableConfig config = RunnableConfig.builder()
                .threadId(session.getSessionId())
                .build();

        try {
            Optional<InterviewState> result = interviewAgent.invoke(initialState, config);

            String currentQuestion = result.map(InterviewState::currentQuestion).orElse("");
            String questionType = result.map(InterviewState::questionType).orElse("技术基础");
            int questionIndex = result.map(InterviewState::questionIndex).orElse(1);

            sessionService.updateStatus(session.getSessionId(), InterviewSessionEntity.Status.IN_PROGRESS.name());

            // 推送问题到前端
            if (!currentQuestion.isEmpty()) {
                webSocketHandler.sendQuestion(session.getSessionId(),
                        QuestionMessage.builder()
                                .content(currentQuestion)
                                .questionType(questionType)
                                .questionIndex(questionIndex)
                                .build());
            }

            return InterviewStartResponse.builder()
                    .sessionId(session.getSessionId())
                    .status("started")
                    .question(InterviewStartResponse.QuestionInfo.builder()
                            .content(currentQuestion)
                            .type(questionType)
                            .index(questionIndex)
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("启动面试失败", e);
            throw new com.zunff.agent.common.exception.BusinessException(1003, "面试启动失败: " + e.getMessage());
        }
    }

    /**
     * 提交答案
     */
    public InterviewAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        String sessionId = request.getSessionId();

        var session = sessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new com.zunff.agent.common.exception.BusinessException(1001, "面试会话不存在");
        }

        Map<String, Object> stateUpdate = new HashMap<>();
        stateUpdate.put(InterviewState.ANSWER_TEXT, request.getAnswerText());
        if (request.getAnswerAudio() != null) {
            stateUpdate.put(InterviewState.ANSWER_AUDIO, request.getAnswerAudio());
        }
        if (request.getAnswerFrames() != null && !request.getAnswerFrames().isEmpty()) {
            stateUpdate.put(InterviewState.ANSWER_FRAMES, request.getAnswerFrames());
        }

        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        try {
            interviewAgent.updateState(config, stateUpdate);
            Optional<InterviewState> result = interviewAgent.invoke(GraphInput.resume(), config);

            boolean isFinished = result.map(InterviewState::isFinished).orElse(false);

            if (isFinished) {
                String report = result.map(InterviewState::getFinalReport).orElse("");
                sessionService.endSession(sessionId);
                sessionService.saveReport(sessionId, report);
                webSocketHandler.sendFinalReport(sessionId, report);
                return InterviewAnswerResponse.finishedWith(report);
            } else {
                String nextQuestion = result.map(InterviewState::currentQuestion).orElse("");
                String questionType = result.map(InterviewState::questionType).orElse("技术基础");
                int questionIndex = result.map(InterviewState::questionIndex).orElse(1);

                if (!nextQuestion.isEmpty()) {
                    webSocketHandler.sendQuestion(sessionId,
                            QuestionMessage.builder()
                                    .content(nextQuestion)
                                    .questionType(questionType)
                                    .questionIndex(questionIndex)
                                    .build());
                }
                return InterviewAnswerResponse.continueWith(nextQuestion, questionType, questionIndex);
            }

        } catch (Exception e) {
            log.error("提交答案失败", e);
            throw new com.zunff.agent.common.exception.BusinessException(1004, "答案提交失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话状态
     */
    public SessionResponse getSessionStatus(String sessionId) {
        var session = sessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new com.zunff.agent.common.exception.BusinessException(1001, "面试会话不存在");
        }

        return SessionResponse.builder()
                .sessionId(session.getSessionId())
                .status(session.getStatus())
                .currentQuestionIndex(session.getCurrentQuestionIndex())
                .createTime(session.getCreateTime())
                .build();
    }

    /**
     * 结束面试
     */
    public ReportResponse endInterview(String sessionId) {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        try {
            Map<String, Object> finalUpdate = new HashMap<>();
            finalUpdate.put(InterviewState.IS_FINISHED, true);
            interviewAgent.updateState(config, finalUpdate);

            StateSnapshot<InterviewState> snapshot = interviewAgent.getState(config);
            String report = snapshot != null && snapshot.state() != null
                    ? snapshot.state().getFinalReport()
                    : "";

            sessionService.endSession(sessionId);
            sessionService.saveReport(sessionId, report);

            return ReportResponse.builder()
                    .sessionId(sessionId)
                    .report(report)
                    .status("ended")
                    .build();

        } catch (Exception e) {
            log.error("结束面试失败", e);
            throw new com.zunff.agent.common.exception.BusinessException(1005, "结束面试失败: " + e.getMessage());
        }
    }

    /**
     * 获取报告
     */
    public ReportResponse getReport(String sessionId) {
        var session = sessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new com.zunff.agent.common.exception.BusinessException(1001, "面试会话不存在");
        }

        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        try {
            StateSnapshot<InterviewState> snapshot = interviewAgent.getState(config);
            String report = snapshot != null && snapshot.state() != null
                    ? snapshot.state().getFinalReport()
                    : session.getReport();

            return ReportResponse.builder()
                    .sessionId(sessionId)
                    .report(report)
                    .status(session.getStatus())
                    .build();

        } catch (Exception e) {
            log.error("获取报告失败", e);
            throw new com.zunff.agent.common.exception.BusinessException(1005, "获取报告失败: " + e.getMessage());
        }
    }
}
