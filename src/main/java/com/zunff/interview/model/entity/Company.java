package com.zunff.interview.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("company")
public class Company {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String companyName;

    private String rawSearchResult;

    private String analyzedResult;

    private LocalDateTime expiresAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer hitCount;
}
