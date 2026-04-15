package com.zunff.interview.agent.nodes;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.model.dto.JobAnalysisResult;
import com.zunff.interview.model.websocket.WebSocketMessage;
import com.zunff.interview.service.extend.PromptTemplateService;
import com.zunff.interview.state.InterviewState;
import com.zunff.interview.utils.JsonExtractionUtils;
import com.zunff.interview.websocket.InterviewWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
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
public class JobAnalysisNode {

    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;
    private final InterviewWebSocketHandler webSocketHandler;

    public JobAnalysisNode(
            ChatClient.Builder chatClientBuilder,
            PromptTemplateService promptTemplateService,
            @Lazy InterviewWebSocketHandler webSocketHandler) {
        this.chatClientBuilder = chatClientBuilder;
        this.promptTemplateService = promptTemplateService;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 执行岗位分析
     */
    public CompletableFuture<Map<String, Object>> execute(InterviewState state) {
        log.info("开始分析岗位类型");

        String jobInfo = state.jobInfo();

        // 从模板加载 system prompt
        String systemPrompt = promptTemplateService.getPrompt("job-analysis");

        // 构建用户提示
        String userPrompt = "Job information:\n" + jobInfo;

        try {
            ChatClient chatClient = chatClientBuilder.build();

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            // 单次响应同时解析：岗位分析结果 + 知识库过滤元数据
            JSONObject json = parseJson(response);
            JobAnalysisResult result = parseJobAnalysisResult(json);
            KnowledgeFilter knowledgeFilter = parseKnowledgeFilter(json);

            if (result == null) {
                // 使用默认配置
                result = getDefaultJobAnalysisResult();
                log.warn("岗位分析解析失败，使用默认配置");
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.JOB_ANALYSIS_RESULT, result);
            updates.put(InterviewState.KNOWLEDGE_COMPANY, knowledgeFilter.company);
            updates.put(InterviewState.KNOWLEDGE_JOB_POSITION, knowledgeFilter.jobPosition);
            CircuitBreakerHelper.recordSuccess(updates);

            // 通知前端岗位分析已完成（并行时前端需等待此信号才能结束自我介绍截断）
            webSocketHandler.sendMessage(state.sessionId(), WebSocketMessage.of(
                    WebSocketMessage.Type.JOB_ANALYSIS_COMPLETE,
                    Map.of(
                            "jobType", result.getJobType().getDisplayName(),
                            "totalQuestions", result.getTotalQuestions()
                    )
            ));

            log.info("岗位分析完成: 类型={}, 技术基础={}, 项目={}, 业务={}, 软技能={}, 公司={}, 岗位={}",
                    result.getJobType().getDisplayName(),
                    result.getTechnicalBasicCount(),
                    result.getProjectCount(),
                    result.getBusinessCount(),
                    result.getSoftSkillCount(),
                    knowledgeFilter.company,
                    knowledgeFilter.jobPosition
            );

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("岗位分析失败", e);
            Map<String, Object> updates = new HashMap<>();
            JobAnalysisResult defaultResult = getDefaultJobAnalysisResult();
            updates.put(InterviewState.JOB_ANALYSIS_RESULT, defaultResult);
            updates.put(InterviewState.KNOWLEDGE_COMPANY, "");
            updates.put(InterviewState.KNOWLEDGE_JOB_POSITION, "");
            CircuitBreakerHelper.handleFailure(state, updates, e);

            // 即使分析失败也发送完成信号，避免前端永久等待
            webSocketHandler.sendMessage(state.sessionId(), WebSocketMessage.of(
                    WebSocketMessage.Type.JOB_ANALYSIS_COMPLETE,
                    Map.of(
                            "jobType", defaultResult.getJobType().getDisplayName(),
                            "totalQuestions", defaultResult.getTotalQuestions()
                    )
            ));

            return CompletableFuture.completedFuture(updates);
        }
    }

    /**
     * 解析岗位分析结果
     */
    private JobAnalysisResult parseJobAnalysisResult(JSONObject json) {
        try {
            Integer jobTypeCode = json.getInt("jobTypeCode");
            String jobTypeValue = json.getStr("jobType", "BALANCED");
            JobAnalysisResult.JobType jobType = jobTypeCode != null
                    ? JobAnalysisResult.JobType.fromCode(jobTypeCode)
                    : JobAnalysisResult.JobType.fromValue(jobTypeValue);

            return JobAnalysisResult.builder()
                    .jobType(jobType)
                    .jobTypeDescription(json.getStr("jobTypeDescription", jobType.getDescription()))
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
                .jobTypeDescription("balanced")
                .technicalBasicCount(3)
                .projectCount(3)
                .businessCount(2)
                .softSkillCount(2)
                .totalQuestions(10)
                .analysisReason("Use default balanced allocation")
                .build();
    }

    private JSONObject parseJson(String response) {
        try {
            String jsonStr = JsonExtractionUtils.extractJsonObjectString(response);
            return JSONUtil.parseObj(jsonStr);
        } catch (Exception e) {
            log.error("解析岗位分析响应失败: {}", e.getMessage());
            return new JSONObject();
        }
    }

    private KnowledgeFilter parseKnowledgeFilter(JSONObject json) {
        return new KnowledgeFilter(
                json.getStr("company", ""),
                json.getStr("jobPosition", "")
        );
    }

    private record KnowledgeFilter(String company, String jobPosition) {
    }
}