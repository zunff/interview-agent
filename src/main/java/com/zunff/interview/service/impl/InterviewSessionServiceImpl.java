package com.zunff.interview.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zunff.interview.mapper.InterviewSessionMapper;
import com.zunff.interview.model.entity.InterviewSession;
import com.zunff.interview.service.extend.AudioStreamService;
import com.zunff.interview.service.InterviewSessionService;
import com.zunff.interview.service.extend.VideoStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 面试会话服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionServiceImpl extends ServiceImpl<InterviewSessionMapper, InterviewSession> implements InterviewSessionService {

    private final VideoStreamService videoStreamService;
    private final AudioStreamService audioStreamService;

    @Override
    @Transactional
    public InterviewSession createSession(String resume, String jobInfo,
                                          int maxQuestions, int maxFollowUps) {
        String sessionId = IdUtil.fastSimpleUUID().substring(0, 16);

        InterviewSession session = InterviewSession.builder()
                .sessionId(sessionId)
                .resume(resume)
                .jobInfo(jobInfo)
                .maxQuestions(maxQuestions)
                .maxFollowUps(maxFollowUps)
                .currentQuestionIndex(0)
                .status(InterviewSession.Status.WAITING.name())
                .createTime(LocalDateTime.now())
                .build();

        save(session);
        log.info("创建面试会话: {}", sessionId);

        return session;
    }

    @Override
    public InterviewSession getBySessionId(String sessionId) {
        return getOne(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getSessionId, sessionId));
    }

    @Override
    @Transactional
    public void updateStatus(String sessionId, String status) {
        InterviewSession session = getBySessionId(sessionId);
        if (session != null) {
            session.setStatus(status);
            updateById(session);
            log.debug("会话 {} 状态更新为: {}", sessionId, status);
        }
    }

    @Override
    @Transactional
    public void endSession(String sessionId) {
        InterviewSession session = getBySessionId(sessionId);
        if (session != null) {
            session.setStatus(InterviewSession.Status.FINISHED.name());
            session.setEndTime(LocalDateTime.now());
            updateById(session);
            videoStreamService.clearSession(sessionId);
            audioStreamService.clearSession(sessionId);
            log.info("面试会话 {} 已结束", sessionId);
        }
    }

    @Override
    @Transactional
    public void disconnectSession(String sessionId) {
        InterviewSession session = getBySessionId(sessionId);
        if (session != null) {
            session.setStatus(InterviewSession.Status.DISCONNECTED.name());
            session.setEndTime(LocalDateTime.now());
            updateById(session);
            videoStreamService.clearSession(sessionId);
            audioStreamService.clearSession(sessionId);
            log.info("面试会话 {} 已标记为断连状态", sessionId);
        }
    }

    @Override
    @Transactional
    public void saveReport(String sessionId, String report) {
        InterviewSession session = getBySessionId(sessionId);
        if (session != null) {
            session.setReport(report);
            updateById(session);
        }
    }
}