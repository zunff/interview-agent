package com.zunff.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zunff.interview.model.entity.InterviewSession;

/**
 * 面试会话服务接口
 */
public interface InterviewSessionService extends IService<InterviewSession> {

    /**
     * 创建新的面试会话
     */
    InterviewSession createSession(String resume, String jobInfo,
                                   int maxQuestions, int maxFollowUps);

    /**
     * 根据会话ID获取会话
     */
    InterviewSession getBySessionId(String sessionId);

    /**
     * 更新会话状态
     */
    void updateStatus(String sessionId, String status);

    /**
     * 结束面试会话
     */
    void endSession(String sessionId);

    /**
     * 保存最终报告
     */
    void saveReport(String sessionId, String report);
}
