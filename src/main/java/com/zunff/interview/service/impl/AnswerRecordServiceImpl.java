package com.zunff.interview.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zunff.interview.mapper.AnswerRecordMapper;
import com.zunff.interview.model.entity.AnswerRecord;
import com.zunff.interview.service.AnswerRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 回答记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerRecordServiceImpl extends ServiceImpl<AnswerRecordMapper, AnswerRecord> implements AnswerRecordService {

    @Override
    @Transactional
    public void recordAnswer(String sessionId, int questionIndex, String question, String answerText) {
        AnswerRecord entity = AnswerRecord.builder()
                .sessionId(sessionId)
                .questionIndex(questionIndex)
                .question(question)
                .answerText(answerText)
                .timestamp(LocalDateTime.now())
                .build();

        save(entity);
        log.info("会话 {} 记录第 {} 题回答", sessionId, questionIndex);
    }

}
