package com.zunff.interview.service;

import com.zunff.interview.agent.nodes.QuestionGeneratorNode;
import com.zunff.interview.common.exception.BusinessException;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.model.dto.request.StartInterviewRequest;
import com.zunff.interview.model.dto.request.SubmitAnswerRequest;
import com.zunff.interview.model.dto.response.InterviewAnswerResponse;
import com.zunff.interview.model.dto.response.InterviewStartResponse;
import com.zunff.interview.model.dto.response.ReportResponse;
import com.zunff.interview.model.dto.response.SessionResponse;
import com.zunff.interview.model.dto.websocket.QuestionMessage;
import com.zunff.interview.model.entity.InterviewSessionEntity;
import com.zunff.interview.state.InterviewState;
import com.zunff.interview.websocket.InterviewWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 面试业务服务
 * 封装面试相关的所有业务逻辑
 */
@Slf4j
@Service
public class InterviewBusinessService {

    private final CompiledGraph<InterviewState> interviewAgent;
    private final InterviewSessionService sessionService;
    private final InterviewWebSocketHandler webSocketHandler;
    private final VideoStreamService videoStreamService;
    private final MultimodalAnalysisService multimodalAnalysisService;
    private final QuestionGeneratorNode questionGeneratorNode;

    public InterviewBusinessService(
            CompiledGraph<InterviewState> interviewAgent,
            InterviewSessionService sessionService,
            @Lazy InterviewWebSocketHandler webSocketHandler,
            VideoStreamService videoStreamService,
            MultimodalAnalysisService multimodalAnalysisService,
            QuestionGeneratorNode questionGeneratorNode) {
        this.interviewAgent = interviewAgent;
        this.sessionService = sessionService;
        this.webSocketHandler = webSocketHandler;
        this.videoStreamService = videoStreamService;
        this.multimodalAnalysisService = multimodalAnalysisService;
        this.questionGeneratorNode = questionGeneratorNode;
    }

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
            String questionType = result.map(InterviewState::questionType).orElse(QuestionType.TECHNICAL_BASIC.getDisplayName());
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
            throw new BusinessException(1003, "面试启动失败: " + e.getMessage());
        }
    }

    /**
     * 提交答案
     * 执行分析逻辑并生成下一个问题
     */
    public InterviewAnswerResponse submitAnswer(SubmitAnswerRequest request) {
        String sessionId = request.getSessionId();

        var session = sessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(1001, "面试会话不存在");
        }

        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        try {
            // 获取当前状态
            StateSnapshot<InterviewState> snapshot = interviewAgent.getState(config);
            if (snapshot == null || snapshot.state() == null) {
                throw new BusinessException(1002, "面试状态不存在");
            }

            InterviewState currentState = snapshot.state();
            String question = currentState.currentQuestion();
            String answerText = request.getAnswerText();
            String questionType = currentState.questionType();

            // 执行多模态分析
            log.info("开始分析答案，问题: {}, 答案长度: {}", questionType, answerText != null ? answerText.length() : 0);

            var visionResult = multimodalAnalysisService.analyzeVideoFrames(null);
            var audioResult = multimodalAnalysisService.analyzeAudio(null);
            var evaluation = multimodalAnalysisService.comprehensiveEvaluate(
                    question, answerText, visionResult, audioResult, "evaluation");

            log.info("评估完成，综合得分: {}, 是否需要追问: {}", evaluation.getOverallScore(), evaluation.isNeedFollowUp());

            // 更新状态
            Map<String, Object> stateUpdate = new HashMap<>();
            stateUpdate.put(InterviewState.ANSWER_TEXT, answerText);
            stateUpdate.put(InterviewState.WAITING_FOR_ANSWER, false);
            stateUpdate.put(InterviewState.CURRENT_EVALUATION, evaluation);
            stateUpdate.put(InterviewState.NEED_FOLLOW_UP, evaluation.isNeedFollowUp());

            interviewAgent.updateState(config, stateUpdate);

            // 生成下一个问题
            log.info("开始生成下一个问题...");
            var newSnapshot = interviewAgent.getState(config);
            InterviewState updatedState = newSnapshot.state();

            Map<String, Object> questionResult = questionGeneratorNode.execute(updatedState).join();

            String newQuestion = (String) questionResult.get(InterviewState.CURRENT_QUESTION);
            String newQuestionType = (String) questionResult.getOrDefault(InterviewState.QUESTION_TYPE, QuestionType.TECHNICAL_BASIC.getDisplayName());
            int newQuestionIndex = updatedState.questionIndex() + 1;

            // 更新状态
            Map<String, Object> finalUpdate = new HashMap<>();
            finalUpdate.put(InterviewState.CURRENT_QUESTION, newQuestion);
            finalUpdate.put(InterviewState.QUESTION_TYPE, newQuestionType);
            finalUpdate.put(InterviewState.QUESTION_INDEX, newQuestionIndex);
            finalUpdate.put(InterviewState.FOLLOW_UP_COUNT, 0);
            finalUpdate.put(InterviewState.WAITING_FOR_ANSWER, true);

            interviewAgent.updateState(config, finalUpdate);

            // 推送新问题到前端
            if (newQuestion != null && !newQuestion.isEmpty()) {
                webSocketHandler.sendQuestion(sessionId,
                        QuestionMessage.builder()
                                .content(newQuestion)
                                .questionType(newQuestionType)
                                .questionIndex(newQuestionIndex)
                                .build());
            }

            log.info("生成新问题成功: {}", newQuestion.substring(0, Math.min(50, newQuestion.length())) + "...");

            return InterviewAnswerResponse.continueWith(newQuestion, newQuestionType, newQuestionIndex);

        } catch (Exception e) {
            log.error("提交答案失败", e);
            throw new BusinessException(1004, "答案提交失败: " + e.getMessage());
        }
    }

    /**
     * 获取会话状态
     */
    public SessionResponse getSessionStatus(String sessionId) {
        var session = sessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(1001, "面试会话不存在");
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
            throw new BusinessException(1005, "结束面试失败: " + e.getMessage());
        }
    }

    /**
     * 获取报告
     */
    public ReportResponse getReport(String sessionId) {
        var session = sessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(1001, "面试会话不存在");
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
            throw new BusinessException(1005, "获取报告失败: " + e.getMessage());
        }
    }
}
