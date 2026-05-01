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
@TableName("spring_ai_chat_memory")
public class ChatMemoryRecord {

    private String conversationId;

    private String type;

    private String content;

    private LocalDateTime timestamp;
}