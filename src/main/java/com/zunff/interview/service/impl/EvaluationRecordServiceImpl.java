package com.zunff.interview.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zunff.interview.mapper.EvaluationRecordMapper;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.entity.EvaluationRecordEntity;
import com.zunff.interview.service.EvaluationRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评估记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationRecordServiceImpl extends ServiceImpl<EvaluationRecordMapper, EvaluationRecordEntity>
        implements EvaluationRecordService {

    @Override
    public List<EvaluationRecordEntity> getBySessionId(String sessionId) {
        return list(new LambdaQueryWrapper<EvaluationRecordEntity>()
                .eq(EvaluationRecordEntity::getSessionId, sessionId)
                .orderByAsc(EvaluationRecordEntity::getQuestionIndex));
    }

    @Override
    @Transactional
    public void saveEvaluation(String sessionId, EvaluationBO evaluation) {
        EvaluationRecordEntity entity = EvaluationRecordEntity.builder()
                .sessionId(sessionId)
                .questionIndex(evaluation.getQuestionIndex())
                .question(evaluation.getQuestion())
                .answer(evaluation.getAnswer())
                .accuracy(evaluation.getAccuracy())
                .logic(evaluation.getLogic())
                .fluency(evaluation.getFluency())
                .confidence(evaluation.getConfidence())
                .emotionScore(evaluation.getEmotionScore())
                .bodyLanguageScore(evaluation.getBodyLanguageScore())
                .voiceToneScore(evaluation.getVoiceToneScore())
                .overallScore(evaluation.getOverallScore())
                .strengths(JSONUtil.parseArray(evaluation.getStrengths()))
                .weaknesses(JSONUtil.parseArray(evaluation.getWeaknesses()))
                .detailedEvaluation(evaluation.getDetailedEvaluation())
                .createTime(LocalDateTime.now())
                .build();

        save(entity);
    }

    @Override
    public Double calculateAverageScore(String sessionId) {
        return baseMapper.calculateAverageOverallScore(sessionId);
    }
}
