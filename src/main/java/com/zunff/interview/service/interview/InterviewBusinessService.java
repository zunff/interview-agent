package com.zunff.interview.service.interview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zunff.interview.agent.names.NodeNames;
import com.zunff.interview.agent.state.InterviewState;
import com.zunff.interview.common.exception.BusinessException;
import com.zunff.interview.common.response.PageResult;
import com.zunff.interview.model.entity.InterviewSession;
import com.zunff.interview.model.request.SubmitAnswerRequest;
import com.zunff.interview.model.response.InterviewHistoryResponse;
import com.zunff.interview.model.response.ReportResponse;
import com.zunff.interview.model.response.SessionResponse;
import com.zunff.interview.service.EvaluationRecordService;
import com.zunff.interview.service.InterviewSessionService;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final ExecutorService virtualThreadExecutor;

    public InterviewBusinessService(
            CompiledGraph<InterviewState> interviewAgent,
            InterviewSessionService sessionService,
            EvaluationRecordService evaluationRecordService,
            @Qualifier("virtualThreadExecutor") ExecutorService virtualThreadExecutor) {
        this.interviewAgent = interviewAgent;
        this.sessionService = sessionService;
        this.evaluationRecordService = evaluationRecordService;
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
}
