package com.zunff.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zunff.agent.model.entity.InterviewSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面试会话 Mapper
 */
@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSessionEntity> {
}
