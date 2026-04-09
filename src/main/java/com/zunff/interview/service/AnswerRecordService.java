package com.zunff.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zunff.interview.model.entity.AnswerRecordEntity;

import java.util.List;

/**
 * 回答记录服务接口
 */
public interface AnswerRecordService extends IService<AnswerRecordEntity> {

    /**
     * 根据会话ID获取所有回答
     */
    List<AnswerRecordEntity> getBySessionId(String sessionId);

    /**
     * 记录回答
     */
    void recordAnswer(String sessionId, int questionIndex, String answerText, String answerAudio, Object answerFrames);

    /**
     * 统计会话的回答数量
     */
    long countBySessionId(String sessionId);
}
