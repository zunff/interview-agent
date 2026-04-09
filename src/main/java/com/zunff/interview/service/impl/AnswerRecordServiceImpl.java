package com.zunff.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zunff.interview.mapper.AnswerRecordMapper;
import com.zunff.interview.model.entity.AnswerRecordEntity;
import com.zunff.interview.service.AnswerRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回答记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerRecordServiceImpl extends ServiceImpl<AnswerRecordMapper, AnswerRecordEntity>
        implements AnswerRecordService {

    @Override
    public List<AnswerRecordEntity> getBySessionId(String sessionId) {
        return list(new LambdaQueryWrapper<AnswerRecordEntity>()
                .eq(AnswerRecordEntity::getSessionId, sessionId)
                .orderByAsc(AnswerRecordEntity::getQuestionIndex));
    }

    @Override
    @Transactional
    public void recordAnswer(String sessionId, int questionIndex, String answerText, String answerAudio, Object answerFrames) {
        AnswerRecordEntity entity = AnswerRecordEntity.builder()
                .sessionId(sessionId)
                .questionIndex(questionIndex)
                .answerText(answerText)
                .answerAudio(answerAudio)
                .answerFrames(answerFrames)
                .timestamp(LocalDateTime.now())
                .build();

        save(entity);
        log.info("会话 {} 记录第 {} 题回答", sessionId, questionIndex);
    }

    @Override
    public long countBySessionId(String sessionId) {
        return count(new LambdaQueryWrapper<AnswerRecordEntity>()
                .eq(AnswerRecordEntity::getSessionId, sessionId));
    }
}
