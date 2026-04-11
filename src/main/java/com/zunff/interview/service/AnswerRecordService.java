package com.zunff.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zunff.interview.model.entity.AnswerRecord;

/**
 * 回答记录服务接口
 */
public interface AnswerRecordService extends IService<AnswerRecord> {

    /**
     * 记录回答
     */
    void recordAnswer(String sessionId, int questionIndex, String question, String answerText);

}
