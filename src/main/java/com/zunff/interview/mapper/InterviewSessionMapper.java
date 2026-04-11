package com.zunff.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zunff.interview.model.entity.InterviewSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面试会话 Mapper
 */
@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {
}
