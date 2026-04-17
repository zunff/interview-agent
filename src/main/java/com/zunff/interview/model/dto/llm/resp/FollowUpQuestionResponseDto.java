package com.zunff.interview.model.dto.llm.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * LLM 返回的追问问题响应 DTO
 * 用于解析追问生成的结构化输出
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpQuestionResponseDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 追问问题文本
     */
    private String followUpQuestion;

    /**
     * 难度等级：easy/medium/hard
     */
    private String difficulty;

    /**
     * 期望关键词列表
     */
    private List<String> expectedKeywords;

    /**
     * 生成原因/意图说明
     */
    private String reason;
}
