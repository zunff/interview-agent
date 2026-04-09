package com.zunff.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zunff.interview.model.entity.InterviewSessionEntity;

/**
 * 面试会话服务接口
 */
public interface InterviewSessionService extends IService<InterviewSessionEntity> {

    /**
     * 创建新的面试会话
     */
    InterviewSessionEntity createSession(String resume, String jobInfo, String interviewType,
                                          int maxQuestions, int maxFollowUps);

    /**
     * 根据会话ID获取会话
     */
    InterviewSessionEntity getBySessionId(String sessionId);

    /**
     * 更新会话状态
     */
    void updateStatus(String sessionId, String status);

    /**
     * 增加问题索引
     */
    void incrementQuestionIndex(String sessionId);

    /**
     * 结束面试会话
     */
    void endSession(String sessionId);

    /**
     * 保存最终报告
     */
    void saveReport(String sessionId, String report);
}
