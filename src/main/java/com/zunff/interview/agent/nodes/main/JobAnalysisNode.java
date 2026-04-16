package com.zunff.interview.agent.nodes.main;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.model.dto.JobAnalysisResult;
import com.zunff.interview.model.dto.llm.resp.JobAnalysisResponseDto;
import com.zunff.interview.model.websocket.WebSocketMessage;
import com.zunff.interview.service.extend.PromptTemplateService;
import com.zunff.interview.agent.state.InterviewState;
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

            JobAnalysisResponseDto response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(JobAnalysisResponseDto.class);

            // 单次响应同时解析：岗位分析结果 + 知识库过滤元数据
            JobAnalysisResult result = parseJobAnalysisResult(response);

            if (result == null) {
                // 使用默认配置
                result = getDefaultJobAnalysisResult();
                log.warn("岗位分析解析失败，使用默认配置");
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put(InterviewState.JOB_ANALYSIS_RESULT, result);
            updates.put(InterviewState.KNOWLEDGE_COMPANY, response.company());
            updates.put(InterviewState.KNOWLEDGE_JOB_POSITION, response.jobPosition());

            // 根据岗位分析结果调整题目数量，取 JobAnalysisResult 和前端参数的最小值
            int technicalQuestionsFromAnalysis = result.getTechnicalRoundTotal();
            int businessQuestionsFromAnalysis = result.getBusinessRoundTotal();
            int maxTechnicalQuestions = Math.min(technicalQuestionsFromAnalysis, state.maxTechnicalQuestions());
            int maxBusinessQuestions = Math.min(businessQuestionsFromAnalysis, state.maxBusinessQuestions());

            updates.put(InterviewState.MAX_TECHNICAL_QUESTIONS, maxTechnicalQuestions);
            updates.put(InterviewState.MAX_BUSINESS_QUESTIONS, maxBusinessQuestions);

            CircuitBreakerHelper.recordSuccess(updates);

            // 通知前端岗位分析已完成（并行时前端需等待此信号才能结束自我介绍截断）
            webSocketHandler.sendMessage(state.sessionId(), WebSocketMessage.of(
                    WebSocketMessage.Type.JOB_ANALYSIS_COMPLETE,
                    Map.of(
                            "jobType", result.getJobType().getDisplayName(),
                            "totalQuestions", result.getTotalQuestions()
                    )
            ));

            log.info("岗位分析完成: 类型={}, 技术基础={}, 项目={}, 业务={}, 软技能={}, 公司={}, 岗位={}, 实际技术轮={}, 实际业务轮={}",
                    result.getJobType().getDisplayName(),
                    result.getTechnicalBasicCount(),
                    result.getProjectCount(),
                    result.getBusinessCount(),
                    result.getSoftSkillCount(),
                    response.company(),
                    response.jobPosition(),
                    maxTechnicalQuestions,
                    maxBusinessQuestions
            );

            return CompletableFuture.completedFuture(updates);

        } catch (Exception e) {
            log.error("岗位分析失败", e);
            Map<String, Object> updates = new HashMap<>();
            JobAnalysisResult defaultResult = getDefaultJobAnalysisResult();
            updates.put(InterviewState.JOB_ANALYSIS_RESULT, defaultResult);
            updates.put(InterviewState.KNOWLEDGE_COMPANY, "");
            updates.put(InterviewState.KNOWLEDGE_JOB_POSITION, "");

            // 失败时使用前端参数作为兜底
            int maxTechnicalQuestions = state.maxTechnicalQuestions();
            int maxBusinessQuestions = state.maxBusinessQuestions();
            updates.put(InterviewState.MAX_TECHNICAL_QUESTIONS, maxTechnicalQuestions);
            updates.put(InterviewState.MAX_BUSINESS_QUESTIONS, maxBusinessQuestions);

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
    private JobAnalysisResult parseJobAnalysisResult(JobAnalysisResponseDto response) {
        try {
            if (response == null) {
                return null;
            }
            Integer jobTypeCode = response.jobTypeCode();
            JobAnalysisResult.JobType jobType = JobAnalysisResult.JobType.fromCode(jobTypeCode);

            return JobAnalysisResult.builder()
                    .jobType(jobType)
                    .technicalBasicCount(response.technicalBasicCount() == null ? 3 : response.technicalBasicCount())
                    .projectCount(response.projectCount() == null ? 3 : response.projectCount())
                    .businessCount(response.businessCount() == null ? 2 : response.businessCount())
                    .softSkillCount(response.softSkillCount() == null ? 2 : response.softSkillCount())
                    .totalQuestions(response.totalQuestions() == null ? 10 : response.totalQuestions())
                    .keyRequirements(response.keyRequirements() == null ? "" : response.keyRequirements())
                    .techStackSummary(response.techStackSummary() == null ? "" : response.techStackSummary())
                    .businessDomain(response.businessDomain() == null ? "" : response.businessDomain())
                    .softSkillsRequired(response.softSkillsRequired() == null ? "" : response.softSkillsRequired())
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
                .technicalBasicCount(3)
                .projectCount(3)
                .businessCount(2)
                .softSkillCount(2)
                .totalQuestions(10)
                .build();
    }
}