package com.zunff.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.entity.EvaluationRecord;

import java.util.List;

/**
 * 评估记录服务接口
 */
public interface EvaluationRecordService extends IService<EvaluationRecord> {

    /**
     * 保存评估结果
     */
    void saveEvaluation(String sessionId, EvaluationBO evaluation);

    /**
     * 计算会话的平均综合评分
     */
    Double calculateAverageScore(String sessionId);

    /**
     * 获取会话的所有评估记录
     */
    List<EvaluationRecord> getBySessionId(String sessionId);
}
