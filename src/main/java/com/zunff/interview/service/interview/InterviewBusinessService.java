package com.zunff.interview.service.interview;

import com.zunff.interview.common.exception.BusinessException;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.entity.InterviewSession;
import com.zunff.interview.model.request.SubmitAnswerRequest;
import com.zunff.interview.model.response.InterviewAnswerResponse;
import com.zunff.interview.model.response.ReportResponse;
import com.zunff.interview.model.response.SessionResponse;
import com.zunff.interview.service.AnswerRecordService;
import com.zunff.interview.service.EvaluationRecordService;
import com.zunff.interview.service.InterviewSessionService;
import com.zunff.interview.state.InterviewState;
import com.zunff.interview.websocket.InterviewWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;

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
    private final AnswerRecordService answerRecordService;
    private final EvaluationRecordService evaluationRecordService;

    public InterviewBusinessService(
            CompiledGraph<InterviewState> interviewAgent,
            InterviewSessionService sessionService,
            @Lazy InterviewWebSocketHandler webSocketHandler,
            AnswerRecordService answerRecordService,
            EvaluationRecordService evaluationRecordService) {
        this.interviewAgent = interviewAgent;
        this.sessionService = sessionService;
        this.webSocketHandler = webSocketHandler;
        this.answerRecordService = answerRecordService;
        this.evaluationRecordService = evaluationRecordService;
    }

    /**
     * 创建会话并执行面试图
     * 供 WebSocket handler 调用
     *
     * @return 图执行结果，如果失败返回 null
     */
    public InterviewState executeInterviewGraph(String sessionId, String resume, String jobInfo,
                                                 int maxTechnicalQuestions, int maxBusinessQuestions, int maxFollowUps) {
        log.info("开始执行面试图，sessionId: {}, 简历长度: {}, 岗位: {}", sessionId, resume.length(), jobInfo);

        Map<String, Object> initialState = new HashMap<>();
        initialState.put(InterviewState.SESSION_ID, sessionId);
        initialState.put(InterviewState.RESUME, resume);
        initialState.put(InterviewState.JOB_INFO, jobInfo);
        initialState.put(InterviewState.MAX_TECHNICAL_QUESTIONS, maxTechnicalQuestions);
        initialState.put(InterviewState.MAX_BUSINESS_QUESTIONS, maxBusinessQuestions);
        initialState.put(InterviewState.MAX_FOLLOW_UPS_TECHNICAL, maxFollowUps);
        initialState.put(InterviewState.MAX_FOLLOW_UPS_BUSINESS, maxFollowUps);

        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        try {
            Optional<InterviewState> result = interviewAgent.invoke(initialState, config);
            sessionService.updateStatus(sessionId, InterviewSession.Status.IN_PROGRESS.name());
            return result.orElse(null);
        } catch (Exception e) {
            log.error("执行面试图失败，sessionId: {}", sessionId, e);
            return null;
        }
    }

    /**
     * 提交答案
     * 使用 GraphInput.resume() 恢复图执行，由图节点完成分析和问题生成
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
            int questionIndex = currentState.questionIndex();

            log.info("提交答案，问题索引: {}, 问题: {}", questionIndex, question);

            // 准备多模态数据
            List<String> frames = null;
            if (request.getVideoFrames() != null && !request.getVideoFrames().isEmpty()) {
                frames = Arrays.asList(request.getVideoFrames().split(","));
                log.info("解析视频帧: {} 帧", frames.size());
            }

            // 更新状态：注入答案和多模态数据
            Map<String, Object> stateUpdate = new HashMap<>();
            stateUpdate.put(InterviewState.ANSWER_TEXT, request.getAnswerText());
            if (frames != null) {
                stateUpdate.put(InterviewState.ANSWER_FRAMES, frames);
                log.info("更新状态：ANSWER_FRAMES 包含 {} 帧", frames.size());
            }
            // 带时间戳的视频帧（用于Omni多模态时间对齐分析）
            if (request.getFramesWithTimestamps() != null && !request.getFramesWithTimestamps().isEmpty()) {
                stateUpdate.put(InterviewState.ANSWER_FRAMES_WITH_TIMESTAMPS, request.getFramesWithTimestamps());
                log.info("更新状态：ANSWER_FRAMES_WITH_TIMESTAMPS 包含 {} 帧", request.getFramesWithTimestamps().size());
            }
            // WAV音频数据（Base64编码）用于Omni多模态综合分析
            if (request.getAnswerAudio() != null && !request.getAnswerAudio().isEmpty()) {
                stateUpdate.put(InterviewState.ANSWER_AUDIO, request.getAnswerAudio());
                log.info("更新状态：ANSWER_AUDIO 长度: {} chars", request.getAnswerAudio().length());
            }
            // 转录条目（带时间戳）用于Omni多模态综合分析
            if (request.getTranscriptEntries() != null && !request.getTranscriptEntries().isEmpty()) {
                stateUpdate.put(InterviewState.TRANSCRIPT_ENTRIES, request.getTranscriptEntries());
                log.info("更新状态：TRANSCRIPT_ENTRIES 包含 {} 条", request.getTranscriptEntries().size());
            }
            interviewAgent.updateState(config, stateUpdate);

            // 验证状态更新
            log.info("状态更新完成，验证状态...");
            StateSnapshot<InterviewState> verifySnapshot = interviewAgent.getState(config);
            log.info("验证：ANSWER_FRAMES 大小 = {}, ANSWER_TEXT 长度 = {}",
                    verifySnapshot.state().answerFrames().size(),
                    verifySnapshot.state().answerText().length());

            log.info("状态已更新，开始恢复图执行...");

            // 使用 GraphInput.resume() 恢复图执行
            // 图会从 askQuestion 之后继续，直接执行分析节点和追问决策
            Optional<InterviewState> result = interviewAgent.invoke(GraphInput.resume(), config);

            if (result.isEmpty()) {
                throw new BusinessException(1004, "图执行未返回结果");
            }

            InterviewState finalState = result.get();
            log.info("图执行完成，当前问题索引: {}", finalState.questionIndex());

            // 获取评估结果并记录
            EvaluationBO evaluation = finalState.getCurrentEvaluation();
            if (evaluation != null) {
                log.info("评估完成，综合得分: {}", evaluation.getOverallScore());

                // 记录回答
                answerRecordService.recordAnswer(
                        sessionId,
                        questionIndex,
                        question,
                        finalState.answerText()
                );

                // 保存评估结果
                evaluationRecordService.saveEvaluation(sessionId, evaluation);

                // 推送评估结果到前端
                webSocketHandler.sendEvaluationResult(sessionId, evaluation);
            }

            // 检查是否结束
            if (finalState.isFinished()) {
                log.info("面试已结束，sessionId: {}", sessionId);
                return InterviewAnswerResponse.finishedWith(finalState.getFinalReport());
            }

            // 新问题由 AskQuestionNode 统一推送，此处只返回响应
            String newQuestion = finalState.currentQuestion();
            String newQuestionType = finalState.questionType();
            int newQuestionIndex = finalState.questionIndex();

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

            // 计算平均分
            Double averageScore = evaluationRecordService.calculateAverageScore(sessionId);
            log.info("面试平均分: {}", averageScore);

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

    /**
     * 自我介绍完成后恢复图执行
     * 流程：更新状态 → 恢复图（profileAnalysis → technicalRound）→ 推送第一道技术题
     * ASR转录已在WebSocket层实时完成
     *
     * @param sessionId       面试会话ID
     * @param transcribedText 自我介绍转录文本
     */
    public void resumeFromSelfIntro(String sessionId, String transcribedText) {
        log.info("开始自我介绍恢复流程，sessionId: {}, 转录文本长度: {}", sessionId,
                transcribedText != null ? transcribedText.length() : 0);

        try {
            RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();

            // 更新状态：写入自我介绍文本和回答文本
            Map<String, Object> stateUpdate = new HashMap<>();
            stateUpdate.put(InterviewState.SELF_INTRO, transcribedText);
            if (transcribedText != null && !transcribedText.isEmpty()) {
                stateUpdate.put(InterviewState.ANSWER_TEXT, transcribedText);
            }
            interviewAgent.updateState(config, stateUpdate);

            // 恢复图执行: profileAnalysis → technicalRound → pause at askQuestion
            Optional<InterviewState> result = interviewAgent.invoke(GraphInput.resume(), config);

            if (result.isEmpty()) {
                throw new BusinessException(1004, "自我介绍后图执行未返回结果");
            }

            sessionService.updateStatus(sessionId, InterviewSession.Status.IN_PROGRESS.name());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("自我介绍恢复流程失败", e);
            throw new BusinessException(1004, "自我介绍处理失败: " + e.getMessage());
        }
    }
}
