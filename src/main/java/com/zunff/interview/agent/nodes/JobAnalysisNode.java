package com.zunff.interview.agent.nodes;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.interview.model.dto.JobAnalysisResult;
import com.zunff.interview.service.PromptTemplateService;
import com.zunff.interview.state.InterviewState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 岗位分析节点
 * 分析岗位类型（技术驱动型/业务驱动型/均衡型）并动态分配题目数量
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobAnalysisNode {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;

    /**
     * 执行岗位分析
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始分析岗位类型");

        String jobInfo = state.jobInfo();

        // 从模板加载 system prompt
        String systemPrompt = promptTemplateService.getPrompt("job-analysis");

        // 构建用户提示
        String userPrompt = "岗位信息：\n" + jobInfo;

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            // 解析响应
            JobAnalysisResult result = parseJobAnalysisResult(response);

            if (result == null) {
                // 使用默认配置
                result = getDefaultJobAnalysisResult();
                log.warn("岗位分析解析失败，使用默认配置");
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.JOB_ANALYSIS_RESULT, result);
            updates.put(InterviewState.CONSECUTIVE_LLM_FAILURES, 0); // LLM 调用成功，重置失败计数

            log.info("岗位分析完成: 类型={}, 技术基础={}, 项目={}, 业务={}, 软技能={}",
                    result.getJobType().getDisplayName(),
                    result.getTechnicalBasicCount(),
                    result.getProjectCount(),
                    result.getBusinessCount(),
                    result.getSoftSkillCount());

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("岗位分析失败", e);
            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.JOB_ANALYSIS_RESULT, getDefaultJobAnalysisResult());
            updates.put(InterviewState.CONSECUTIVE_LLM_FAILURES, state.consecutiveLLMFailures() + 1);
            if (state.consecutiveLLMFailures() + 1 >= state.maxLLMFailures()) {
                throw new RuntimeException("LLM 连续调用失败达到 " + state.maxLLMFailures() + " 次，触发熔断", e);
            }
            return CompletableFuture.completedFuture(updates);
        }
    }

    /**
     * 解析岗位分析结果
     */
    private JobAnalysisResult parseJobAnalysisResult(String response) {
        try {
            String jsonStr = extractJson(response);
            JSONObject json = JSONUtil.parseObj(jsonStr);

            JobAnalysisResult.JobType jobType;
            try {
                jobType = JobAnalysisResult.JobType.valueOf(json.getStr("jobType", "BALANCED"));
            } catch (IllegalArgumentException e) {
                jobType = JobAnalysisResult.JobType.BALANCED;
            }

            return JobAnalysisResult.builder()
                    .jobType(jobType)
                    .jobTypeDescription(json.getStr("jobTypeDescription", "均衡型"))
                    .technicalBasicCount(json.getInt("technicalBasicCount", 3))
                    .projectCount(json.getInt("projectCount", 3))
                    .businessCount(json.getInt("businessCount", 2))
                    .softSkillCount(json.getInt("softSkillCount", 2))
                    .totalQuestions(json.getInt("totalQuestions", 10))
                    .analysisReason(json.getStr("analysisReason", ""))
                    .build();

        } catch (Exception e) {
            log.error("解析岗位分析结果失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取默认岗位分析结果
     */
    private JobAnalysisResult getDefaultJobAnalysisResult() {
        return JobAnalysisResult.builder()
                .jobType(JobAnalysisResult.JobType.BALANCED)
                .jobTypeDescription("均衡型")
                .technicalBasicCount(3)
                .projectCount(3)
                .businessCount(2)
                .softSkillCount(2)
                .totalQuestions(10)
                .analysisReason("使用默认均衡配置")
                .build();
    }

    /**
     * 提取 JSON 字符串
     */
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }
}