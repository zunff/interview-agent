package com.zunff.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.entity.EvaluationRecordEntity;

import java.util.List;

/**
 * 评估记录服务接口
 */
public interface EvaluationRecordService extends IService<EvaluationRecordEntity> {

    /**
     * 根据会话ID获取所有评估
     */
    List<EvaluationRecordEntity> getBySessionId(String sessionId);

    /**
     * 保存评估结果
     */
    void saveEvaluation(String sessionId, EvaluationBO evaluation);

    /**
     * 计算会话的平均综合评分
     */
    Double calculateAverageScore(String sessionId);
}
