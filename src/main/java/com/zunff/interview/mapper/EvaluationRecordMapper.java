package com.zunff.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zunff.interview.model.entity.EvaluationRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 评估记录 Mapper
 */
@Mapper
public interface EvaluationRecordMapper extends BaseMapper<EvaluationRecordEntity> {

    /**
     * 计算会话的平均综合评分
     */
    @Select("SELECT AVG(overall_score) FROM evaluation_record WHERE session_id = #{sessionId}")
    Double calculateAverageOverallScore(String sessionId);
}
