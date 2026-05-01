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
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private String resumeFilePath;

    private String resumeText;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime endTime;

    public enum Status {
        ACTIVE, ENDED
    }
}
