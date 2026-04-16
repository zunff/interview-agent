package com.zunff.interview.model.dto.llm.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LLM 批量题目生成响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmQuestionListResultDto {

    /**
     * 生成的题目列表
     */
    private List<QuestionDto> questions;

    /**
     * 单个题目的 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDto {
        /**
         * 题目内容
         */
        private String question;

        /**
         * 题目类型代码
         */
        private Integer questionTypeCode;

        /**
         * 期望关键词
         */
        private List<String> expectedKeywords;

        /**
         * 难度
         */
        private String difficulty;

        /**
         * 生成原因/意图
         */
        private String reason;
    }
}
