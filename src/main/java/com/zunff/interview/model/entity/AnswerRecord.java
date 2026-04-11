package com.zunff.interview.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 回答记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("answer_record")
public class AnswerRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的会话ID */
    private String sessionId;

    /** 问题索引 */
    private Integer questionIndex;

    /** 问题内容 */
    private String question;

    /** 回答文本 */
    private String answerText;

    /** 时间戳 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime timestamp;
}
