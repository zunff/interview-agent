package com.zunff.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zunff.interview.mapper.EvaluationRecordMapper;
import com.zunff.interview.model.bo.EvaluationBO;
import com.zunff.interview.model.entity.EvaluationRecord;
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
public class EvaluationRecordServiceImpl extends ServiceImpl<EvaluationRecordMapper, EvaluationRecord> implements EvaluationRecordService {

    @Override
    @Transactional
    public void saveEvaluation(String sessionId, EvaluationBO evaluation) {
        EvaluationRecord entity = EvaluationRecord.builder()
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
                .strengths(evaluation.getStrengths())
                .weaknesses(evaluation.getWeaknesses())
                .detailedEvaluation(evaluation.getDetailedEvaluation())
                .createTime(LocalDateTime.now())
                .build();

        save(entity);
    }

    @Override
    public Double calculateAverageScore(String sessionId) {
        return baseMapper.calculateAverageOverallScore(sessionId);
    }

    @Override
    public List<EvaluationRecord> getBySessionId(String sessionId) {
        return list(new LambdaQueryWrapper<EvaluationRecord>()
                .eq(EvaluationRecord::getSessionId, sessionId)
                .orderByAsc(EvaluationRecord::getQuestionIndex));
    }
}
