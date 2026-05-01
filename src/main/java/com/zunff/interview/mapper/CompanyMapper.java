package com.zunff.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zunff.interview.model.entity.Company;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CompanyMapper extends BaseMapper<Company> {

    @Select("SELECT * FROM company WHERE company_name = #{companyName} " +
            "AND (expires_at IS NULL OR expires_at > #{now}) " +
            "LIMIT 1")
    Company findActiveByName(@Param("companyName") String companyName,
                             @Param("now") LocalDateTime now);

    @Select("<script>" +
            "SELECT * FROM company WHERE company_name IN " +
            "<foreach collection='companyNames' item='name' open='(' separator=',' close=')'>" +
            "#{name}" +
            "</foreach>" +
            " AND (expires_at IS NULL OR expires_at > #{now})" +
            "</script>")
    List<Company> findActiveByNames(@Param("companyNames") List<String> companyNames,
                                     @Param("now") LocalDateTime now);

    @Update("UPDATE company SET hit_count = hit_count + 1, update_time = #{now} WHERE id = #{id}")
    int incrementHitCount(@Param("id") Long id, @Param("now") LocalDateTime now);
}
