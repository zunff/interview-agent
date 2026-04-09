package com.zunff.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zunff.interview.model.entity.InterviewKnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 面试知识库 Mapper
 */
@Mapper
public interface InterviewKnowledgeMapper extends BaseMapper<InterviewKnowledgeEntity> {
}