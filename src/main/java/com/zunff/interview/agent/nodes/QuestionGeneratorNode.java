package com.zunff.interview.agent.nodes;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.constant.InterviewRound;
import com.zunff.interview.constant.QuestionType;
import com.zunff.interview.model.dto.KnowledgeSearchResult;
import com.zunff.interview.service.InterviewKnowledgeService;
import com.zunff.interview.service.PromptTemplateService;
import com.zunff.interview.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 问题生成节点
 * 根据简历、岗位信息和面试类型生成面试问题
 * 支持从知识库检索参考题目
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionGeneratorNode {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;
    private final InterviewKnowledgeService knowledgeService;

    @Value("${interview.knowledge.enabled:true}")
    private boolean knowledgeEnabled;

    /**
     * 执行问题生成
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始生成面试问题，当前轮次: {}, 问题索引: {}", state.currentRound(), state.questionIndex());

        String resume = state.resume();
        String jobInfo = state.jobInfo();
        String interviewType = state.interviewType();
        List<String> previousQuestions = state.questions();
        int questionIndex = state.questionIndex();
        String currentRound = state.currentRound();
        InterviewRound round = InterviewRound.fromCode(currentRound);

        // 根据轮次选择不同的 Prompt 模板
        String systemPrompt = promptTemplateService.getPrompt(round.getPromptTemplate());

        // 构建用户提示
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("候选人简历：\n").append(resume).append("\n\n");
        userPrompt.append("应聘岗位：\n").append(jobInfo).append("\n\n");
        userPrompt.append("面试类型：").append(interviewType).append("\n\n");
        userPrompt.append("当前轮次：").append(round.getDisplayName()).append("\n\n");

        // 根据轮次显示已问问题数
        if (round.isTechnical()) {
            userPrompt.append("技术轮已问问题数：").append(state.technicalQuestionsDone()).append("/").append(state.maxTechnicalQuestions()).append("\n\n");
        } else {
            userPrompt.append("业务轮已问问题数：").append(state.businessQuestionsDone()).append("/").append(state.maxBusinessQuestions()).append("\n\n");
        }

        userPrompt.append("当前问题序号：").append(questionIndex + 1).append("\n\n");

        if (!previousQuestions.isEmpty()) {
            userPrompt.append("已提问的问题：\n");
            for (int i = 0; i < previousQuestions.size(); i++) {
                userPrompt.append((i + 1)).append(". ").append(previousQuestions.get(i)).append("\n");
            }
            userPrompt.append("\n请生成下一个问题，避免与已提问的问题重复。\n");
        } else {
            if (round.isTechnical()) {
                userPrompt.append("这是技术轮的第一个问题，请从技术基础或项目经验方面入手。\n");
            } else {
                userPrompt.append("这是业务轮的第一个问题，请从业务场景或软技能方面入手。\n");
            }
        }

        // 从知识库检索参考题目
        String referenceContext = "";
        if (knowledgeEnabled) {
            referenceContext = searchReferenceQuestions(jobInfo, round);
            if (!referenceContext.isEmpty()) {
                userPrompt.append("\n--- 可选参考题目（仅供参考，请生成新的问题）---\n");
                userPrompt.append(referenceContext);
            }
        }

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt.toString())
                    .call()
                    .content();

            // 解析响应
            QuestionResult result = parseQuestionResult(response);

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.CURRENT_QUESTION, result.question);
            updates.put(InterviewState.QUESTION_TYPE, result.questionType);
            updates.put(InterviewState.QUESTION_INDEX, questionIndex + 1);
            updates.put(InterviewState.FOLLOW_UP_COUNT, 0); // 重置追问计数
            CircuitBreakerHelper.recordSuccess(updates);

            log.info("生成问题成功: [{}] {}", result.questionType, result.question);

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("生成问题失败", e);
            Map<String, Object> updates = new HashMap<>();
            // 返回默认问题
            updates.put(InterviewState.CURRENT_QUESTION, "请简单介绍一下你的技术背景和项目经验。");
            updates.put(InterviewState.QUESTION_TYPE, "项目经验");
            updates.put(InterviewState.QUESTION_INDEX, questionIndex + 1);
            updates.put(InterviewState.FOLLOW_UP_COUNT, 0);
            CircuitBreakerHelper.handleFailure(state, updates, e);
            return CompletableFuture.completedFuture(updates);
        }
    }

    /**
     * 从知识库检索参考题目
     */
    private String searchReferenceQuestions(String jobInfo, InterviewRound round) {
        try {
            String questionType = round.isTechnical() ? "技术面" : "业务面";
            List<KnowledgeSearchResult> results = knowledgeService.searchByJobInfo(jobInfo, questionType, 3);

            if (results.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                KnowledgeSearchResult r = results.get(i);
                sb.append(i + 1).append(". ").append(r.getQuestion()).append("\n");
                if (r.getCompany() != null && !r.getCompany().isEmpty()) {
                    sb.append("   公司：").append(r.getCompany()).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("知识库检索失败: {}", e.getMessage());
            return "";
        }
    }

    private QuestionResult parseQuestionResult(String response) {
        try {
            String jsonStr = extractJson(response);
            JSONObject json = JSONUtil.parseObj(jsonStr);

            List<String> keywords = new ArrayList<>();
            JSONArray keywordsArray = json.getJSONArray("expectedKeywords");
            if (keywordsArray != null && !keywordsArray.isEmpty()) {
                for (int i = 0; i < keywordsArray.size(); i++) {
                    keywords.add(keywordsArray.getStr(i));
                }
            }

            return QuestionResult.builder()
                    .question(json.getStr("question", response))
                    .questionType(json.getStr("questionType", QuestionType.TECHNICAL_BASIC.getDisplayName()))
                    .expectedKeywords(keywords)
                    .difficulty(json.getStr("difficulty", "中等"))
                    .reason(json.getStr("reason", json.getStr("interviewIntent", "")))
                    .build();
        } catch (Exception e) {
            log.error("解析问题结果失败: {}", e.getMessage());
            return QuestionResult.builder()
                    .question(response)
                    .questionType(QuestionType.TECHNICAL_BASIC.getDisplayName())
                    .difficulty("中等")
                    .build();
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class QuestionResult {
        private String question;
        private String questionType;
        private List<String> expectedKeywords;
        private String difficulty;
        private String reason;
    }
}