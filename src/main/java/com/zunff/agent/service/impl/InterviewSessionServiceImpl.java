package com.zunff.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zunff.agent.mapper.InterviewSessionMapper;
import com.zunff.agent.model.entity.InterviewSessionEntity;
import com.zunff.agent.service.InterviewSessionService;
import com.zunff.agent.service.VideoStreamService;
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
public class InterviewSessionServiceImpl extends ServiceImpl<InterviewSessionMapper, InterviewSessionEntity>
        implements InterviewSessionService {

    private final VideoStreamService videoStreamService;

    @Override
    @Transactional
    public InterviewSessionEntity createSession(String resume, String jobInfo, String interviewType,
                                                 int maxQuestions, int maxFollowUps) {
        String sessionId = IdUtil.fastSimpleUUID().substring(0, 16);

        InterviewSessionEntity session = InterviewSessionEntity.builder()
                .sessionId(sessionId)
                .resume(resume)
                .jobInfo(jobInfo)
                .interviewType(interviewType)
                .maxQuestions(maxQuestions)
                .maxFollowUps(maxFollowUps)
                .currentQuestionIndex(0)
                .status(InterviewSessionEntity.Status.WAITING.name())
                .createTime(LocalDateTime.now())
                .build();

        save(session);
        log.info("创建面试会话: {}", sessionId);

        return session;
    }

    @Override
    public InterviewSessionEntity getBySessionId(String sessionId) {
        return getOne(new LambdaQueryWrapper<InterviewSessionEntity>()
                .eq(InterviewSessionEntity::getSessionId, sessionId));
    }

    @Override
    @Transactional
    public void updateStatus(String sessionId, String status) {
        InterviewSessionEntity session = getBySessionId(sessionId);
        if (session != null) {
            session.setStatus(status);
            updateById(session);
            log.debug("会话 {} 状态更新为: {}", sessionId, status);
        }
    }

    @Override
    @Transactional
    public void incrementQuestionIndex(String sessionId) {
        InterviewSessionEntity session = getBySessionId(sessionId);
        if (session != null) {
            session.setCurrentQuestionIndex(session.getCurrentQuestionIndex() + 1);
            updateById(session);
        }
    }

    @Override
    @Transactional
    public void endSession(String sessionId) {
        InterviewSessionEntity session = getBySessionId(sessionId);
        if (session != null) {
            session.setStatus(InterviewSessionEntity.Status.FINISHED.name());
            session.setEndTime(LocalDateTime.now());
            updateById(session);
            videoStreamService.clearSession(sessionId);
            log.info("面试会话 {} 已结束", sessionId);
        }
    }

    @Override
    @Transactional
    public void saveReport(String sessionId, String report) {
        InterviewSessionEntity session = getBySessionId(sessionId);
        if (session != null) {
            session.setReport(report);
            updateById(session);
        }
    }
}