package com.zunff.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zunff.interview.model.entity.AnswerRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回答记录 Mapper
 */
@Mapper
public interface AnswerRecordMapper extends BaseMapper<AnswerRecordEntity> {
}
