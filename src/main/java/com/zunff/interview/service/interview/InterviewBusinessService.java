package com.zunff.interview.service.interview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zunff.interview.agent.checkpoint.PostgresCheckpointSaver;
import com.zunff.interview.agent.names.NodeNames;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.common.exception.BusinessException;
import com.zunff.interview.common.response.PageResult;
import com.zunff.interview.model.bo.GeneratedQuestion;
import com.zunff.interview.model.entity.InterviewSession;
import com.zunff.interview.model.request.SubmitAnswerRequest;
import com.zunff.interview.model.response.InterviewHistoryResponse;
import com.zunff.interview.model.response.ReportResponse;
import com.zunff.interview.model.response.SessionResponse;
import com.zunff.interview.model.websocket.QuestionMessage;
import com.zunff.interview.model.websocket.WebSocketMessage;
import com.zunff.interview.service.EvaluationRecordService;
import com.zunff.interview.service.InterviewSessionService;
import com.zunff.interview.websocket.InterviewWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ExecutorService;

/**
 * 面试业务服务
 * 封装面试相关的所有业务逻辑
 */
@Slf4j
@Service
public class InterviewBusinessService {

    private final CompiledGraph<InterviewState> interviewAgent;
    private final InterviewSessionService sessionService;
    private final EvaluationRecordService evaluationRecordService;
    private final PostgresCheckpointSaver checkpointSaver;
    private final InterviewWebSocketHandler webSocketHandler;
    private final ExecutorService virtualThreadExecutor;

    public InterviewBusinessService(
            CompiledGraph<InterviewState> interviewAgent,
            InterviewSessionService sessionService,
            EvaluationRecordService evaluationRecordService,
            PostgresCheckpointSaver checkpointSaver,
            @Lazy InterviewWebSocketHandler webSocketHandler,
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.interviewAgent = interviewAgent;
        this.sessionService = sessionService;
        this.evaluationRecordService = evaluationRecordService;
        this.checkpointSaver = checkpointSaver;
        this.webSocketHandler = webSocketHandler;
        this.virtualThreadExecutor = virtualThreadExecutor;
    }

    /**
     * 分页获取面试历史列表
     *
     * @param page    页码
     * @param size    每页条数
     * @param keyword 搜索关键词（岗位信息模糊匹配）
     */
    public PageResult<InterviewHistoryResponse> listSessions(int page, int size, String keyword) {
        LambdaQueryWrapper<InterviewSession> wrapper = new LambdaQueryWrapper<InterviewSession>()
                .orderByDesc(InterviewSession::getCreateTime);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(InterviewSession::getJobInfo, keyword);
        }

        Page<InterviewSession> pageResult = sessionService.page(new Page<>(page, size), wrapper);

        List<InterviewHistoryResponse> records = new ArrayList<>(pageResult.getRecords().size());
        for (InterviewSession s : pageResult.getRecords()) {
            records.add(InterviewHistoryResponse.builder()
                    .sessionId(s.getSessionId())
                    .jobInfo(s.getJobInfo())
                    .status(s.getStatus())
                    .currentQuestionIndex(s.getCurrentQuestionIndex())
                    .maxTechnicalQuestions(s.getMaxTechnicalQuestions())
                    .maxBusinessQuestions(s.getMaxBusinessQuestions())
                    .createTime(s.getCreateTime())
                    .endTime(s.getEndTime())
                    .build());
        }

        return PageResult.of(pageResult, records);
    }

    /**
     * 创建会话并执行面试图
     * 供 WebSocket handler 调用
     *
     * @return 图执行结果，如果失败返回 null
     */
    public InterviewState executeInterviewGraph(String sessionId, String resume, String jobInfo,
                                                 int maxTechnicalQuestions, int maxBusinessQuestions, int maxFollowUps,
                                                 String positionLevel) {
        log.info("开始执行面试图，sessionId: {}, 简历长度: {}, 岗位: {}, 级别: {}",
                sessionId, resume.length(), jobInfo, positionLevel);

        Map<String, Object> initialState = new HashMap<>();
        initialState.put(InterviewState.SESSION_ID, sessionId);
        initialState.put(InterviewState.RESUME, resume);
        initialState.put(InterviewState.JOB_INFO, jobInfo);
        initialState.put(InterviewState.MAX_TECHNICAL_QUESTIONS, maxTechnicalQuestions);
        initialState.put(InterviewState.MAX_BUSINESS_QUESTIONS, maxBusinessQuestions);
        initialState.put(InterviewState.MAX_FOLLOW_UPS_TECHNICAL, maxFollowUps);
        initialState.put(InterviewState.MAX_FOLLOW_UPS_BUSINESS, maxFollowUps);
        initialState.put(InterviewState.POSITION_LEVEL, positionLevel != null ? positionLevel : "");

        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .addParallelNodeExecutor(NodeNames.INIT, virtualThreadExecutor)
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
     * 更新图状态后恢复图执行，后续由图节点完成评估、持久化和问题推送
     */
    public void submitAnswer(SubmitAnswerRequest request) {
        String sessionId = request.getSessionId();

        var session = sessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(1001, "面试会话不存在");
        }

        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        try {
            // 更新状态：注入答案和多模态数据
            Map<String, Object> stateUpdate = new HashMap<>();
            stateUpdate.put(InterviewState.ANSWER_TEXT, request.getAnswerText());

            List<String> frames = null;
            if (request.getVideoFrames() != null && !request.getVideoFrames().isEmpty()) {
                frames = Arrays.asList(request.getVideoFrames().split(","));
                stateUpdate.put(InterviewState.ANSWER_FRAMES, frames);
            }
            if (request.getFramesWithTimestamps() != null && !request.getFramesWithTimestamps().isEmpty()) {
                stateUpdate.put(InterviewState.ANSWER_FRAMES_WITH_TIMESTAMPS, request.getFramesWithTimestamps());
            }
            if (request.getAnswerAudio() != null && !request.getAnswerAudio().isEmpty()) {
                stateUpdate.put(InterviewState.ANSWER_AUDIO, request.getAnswerAudio());
            }
            if (request.getTranscriptEntries() != null && !request.getTranscriptEntries().isEmpty()) {
                stateUpdate.put(InterviewState.TRANSCRIPT_ENTRIES, request.getTranscriptEntries());
            }

            interviewAgent.updateState(config, stateUpdate);
            log.info("状态已更新，恢复图执行: sessionId={}", sessionId);

            interviewAgent.invoke(GraphInput.resume(), config);

        } catch (Exception e) {
            log.error("提交答案失败: sessionId={}", sessionId, e);
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
     * 结束面试（手动提前结束）
     */
    public ReportResponse endInterview(String sessionId) {
        var session = sessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(1001, "面试会话不存在");
        }

        try {
            // 标记面试结束
            sessionService.endSession(sessionId);

            // 计算平均分
            Double averageScore = evaluationRecordService.calculateAverageScore(sessionId);
            log.info("面试提前结束，平均分: {}", averageScore);

            // 从数据库获取报告（如果已生成）
            String report = session.getReport();

            return ReportResponse.builder()
                    .sessionId(sessionId)
                    .report(report != null ? report : "")
                    .status("ended")
                    .build();

        } catch (Exception e) {
            log.error("结束面试失败", e);
            throw new BusinessException(1005, "结束面试失败: " + e.getMessage());
        }
    }

    /**
     * 获取报告（从数据库读取）
     */
    public ReportResponse getReport(String sessionId) {
        var session = sessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(1001, "面试会话不存在");
        }

        String report = session.getReport();
        if (report == null || report.isEmpty()) {
            throw new BusinessException(1005, "报告尚未生成");
        }

        return ReportResponse.builder()
                .sessionId(sessionId)
                .report(report)
                .status(session.getStatus())
                .build();
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

    /**
     * 恢复断连的面试
     * 从 DB 加载 checkpoint 恢复图执行
     *
     * @param sessionId 面试会话ID
     */
    public void resumeInterview(String sessionId) {
        log.info("开始恢复面试，sessionId: {}", sessionId);

        // 1. 验证会话存在
        InterviewSession session = sessionService.getBySessionId(sessionId);
        if (session == null) {
            throw new BusinessException(1001, "面试会话不存在");
        }

        // 2. 推送恢复成功消息
        webSocketHandler.sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.INTERVIEW_RESUMED,
                Map.of("sessionId", sessionId)
        ));

        // 3. 从 checkpoint 获取当前状态
        Optional<Checkpoint> checkpointOpt = checkpointSaver.getLatestCheckpoint(sessionId);
        if (checkpointOpt.isEmpty()) {
            log.warn("无可用 checkpoint，无法恢复: sessionId={}", sessionId);
            sendSelfIntroSignal(sessionId);
            sessionService.updateStatus(sessionId, InterviewSession.Status.IN_PROGRESS.name());
            return;
        }

        Checkpoint checkpoint = checkpointOpt.get();
        String nodeId = checkpoint.getNodeId();
        String nextNodeId = checkpoint.getNextNodeId();

        log.info("Checkpoint loaded: sessionId={}, nodeId={}, nextNodeId={}", sessionId, nodeId, nextNodeId);

        // 4. 根据 checkpoint 判断恢复策略
        // 情况1：在 PROFILE_ANALYSIS 前中断（自我介绍已完成，等待分析）
        if (NodeNames.PROFILE_ANALYSIS.equals(nextNodeId)) {
            log.info("图在 PROFILE_ANALYSIS 前中断，恢复执行画像分析: sessionId={}", sessionId);
            resumeFromProfileAnalysis(sessionId, checkpoint);
            sessionService.updateStatus(sessionId, InterviewSession.Status.IN_PROGRESS.name());
            return;
        }

        // 情况2：在 askQuestion 后中断（等待回答）
        // nodeId 格式："technicalRound-tech_askQuestion" 或 "businessRound-biz_askQuestion"
        if (nodeId != null && nodeId.contains("_" + NodeNames.ASK_QUESTION)) {
            log.info("图在 askQuestion 后中断，重推当前题目: sessionId={}", sessionId);
            rePushCurrentQuestion(sessionId, checkpoint);
            sessionService.updateStatus(sessionId, InterviewSession.Status.IN_PROGRESS.name());
            return;
        }

        // 情况3：其他节点中断或新面试（直接进入自我介绍）
        log.info("未知中断点，默认推送自我介绍信号: sessionId={}, nodeId={}, nextNodeId={}",
                sessionId, nodeId, nextNodeId);
        sendSelfIntroSignal(sessionId);
        sessionService.updateStatus(sessionId, InterviewSession.Status.IN_PROGRESS.name());
    }

    /**
     * 从 PROFILE_ANALYSIS 节点恢复执行
     */
    private void resumeFromProfileAnalysis(String sessionId, Checkpoint checkpoint) {
        try {
            // 构建 RunnableConfig
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .addParallelNodeExecutor(NodeNames.INIT, virtualThreadExecutor)
                    .build();

            // 从 checkpoint 恢复图执行（LangGraph4j 会自动加载状态）
            interviewAgent.invoke(GraphInput.resume(), config);

            log.info("已从 PROFILE_ANALYSIS 恢复执行: sessionId={}", sessionId);

        } catch (Exception e) {
            log.error("PROFILE_ANALYSIS 恢复失败: sessionId={}", sessionId, e);
            throw new BusinessException(1004, "画像分析恢复失败: " + e.getMessage());
        }
    }

    /**
     * 从 checkpoint 重新推送当前题目
     */
    private void rePushCurrentQuestion(String sessionId, Checkpoint checkpoint) {
        try {
            // 从 checkpoint.state 中获取当前题目
            Map<String, Object> state = checkpoint.getState();

            // 获取当前生成的题目（根据轮次判断）
            String currentQuestionKey = state.containsKey("currentGeneratedQuestion")
                ? "currentGeneratedQuestion"
                : "mainGeneratedQuestion";

            Object questionObj = state.get(currentQuestionKey);
            if (questionObj == null) {
                log.warn("checkpoint 中无当前题目: sessionId={}", sessionId);
                return;
            }

            // 将 state 中的题目对象转为 QuestionMessage
            QuestionMessage questionMessage = convertToQuestionMessage(questionObj);
            webSocketHandler.sendQuestion(sessionId, questionMessage);

            log.info("已重推当前题目: sessionId={}, question={}", sessionId, questionMessage.getContent());
        } catch (Exception e) {
            log.error("重推当前题目失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 发送自我介绍阶段信号
     */
    private void sendSelfIntroSignal(String sessionId) {
        webSocketHandler.sendMessage(sessionId, WebSocketMessage.of(
                WebSocketMessage.Type.SELF_INTRO,
                Map.of()
        ));
        log.info("已重推自我介绍信号: sessionId={}", sessionId);
    }

    /**
     * 将 checkpoint 中的题目对象转换为 QuestionMessage
     */
    private QuestionMessage convertToQuestionMessage(Object questionObj) {
        // checkpoint 存的就是 GeneratedQuestion，恢复后也是 GeneratedQuestion
        GeneratedQuestion gq = (GeneratedQuestion) questionObj;
        return QuestionMessage.builder()
                .content(gq.getQuestion())
                .questionType(gq.getQuestionType())
                .questionIndex(gq.getQuestionIndex())
                .isFollowUp(gq.getQuestionType() != null && gq.getQuestionType().contains("追问"))
                .build();
    }
}
