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
@TableName("resume_analysis")
public class ResumeAnalysis {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String analysisId;

    private String sessionId;

    private String fileName;

    private String fileType;

    private String resumeText;

    private String status;

    private String reportMarkdown;

    private String radarChartData;

    private LocalDateTime createTime;

    private LocalDateTime endTime;

    public enum Status {
        PENDING, ANALYZING, DONE, FAILED
    }
}
