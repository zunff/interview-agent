package com.zunff.interview.agent.nodes.main;

import com.zunff.interview.agent.CircuitBreakerHelper;
import com.zunff.interview.model.bo.JobAnalysisResult;
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

        // 构建用户提示（注入前端限制）
        String userPrompt = promptTemplateService.getPrompt("job-analysis-user", Map.of(
                "jobInfo", jobInfo,
                "maxTechnicalQuestions", String.valueOf(state.maxTechnicalQuestions()),
                "maxBusinessQuestions", String.valueOf(state.maxBusinessQuestions())
        ));

        try {
            ChatClient chatClient = chatClientBuilder.build();

            JobAnalysisResponseDto response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(JobAnalysisResponseDto.class);

            // 单次响应同时解析：岗位分析结果 + 知识库过滤元数据
            JobAnalysisResult result = parseJobAnalysisResult(response, state.maxTechnicalQuestions(), state.maxBusinessQuestions());

            if (result == null) {
                // 使用默认配置
                result = getDefaultJobAnalysisResult();
                log.warn("岗位分析解析失败，使用默认配置");
            }

            // 处理岗位级别（优先级：前端传入 > LLM返回 > 默认）
            JobAnalysisResult.PositionLevel positionLevel = determinePositionLevel(state, response);
            result.setPositionLevel(positionLevel);

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
                            "totalQuestions", result.getTotalQuestions(),
                            "positionLevel", positionLevel.getDisplayName()
                    )
            ));

            log.info("岗位分析完成: 类型={}, 级别={}, 技术基础={}, 项目={}, 业务={}, 软技能={}, 公司={}, 岗位={}, 实际技术轮={}, 实际业务轮={}",
                    result.getJobType().getDisplayName(),
                    positionLevel.getDisplayName(),
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
                            "totalQuestions", defaultResult.getTotalQuestions(),
                            "positionLevel", "Mid-Level"
                    )
            ));

            return CompletableFuture.completedFuture(updates);
        }
    }

    /**
     * 确定岗位级别（优先级：前端传入 > LLM返回 > 默认）
     */
    private JobAnalysisResult.PositionLevel determinePositionLevel(InterviewState state, JobAnalysisResponseDto response) {
        // 1. 前端传入级别
        String positionLevelStr = state.positionLevel();
        if (positionLevelStr != null && !positionLevelStr.isEmpty()) {
            return JobAnalysisResult.PositionLevel.fromValue(positionLevelStr);
        }

        // 2. LLM 返回的级别
        if (response != null && response.positionLevelCode() != null) {
            return JobAnalysisResult.PositionLevel.fromCode(response.positionLevelCode());
        }

        // 3. 默认
        return JobAnalysisResult.PositionLevel.MID_LEVEL;
    }

    /**
     * 解析岗位分析结果（含超限兜底）
     */
    private JobAnalysisResult parseJobAnalysisResult(JobAnalysisResponseDto response, int maxTechnical, int maxBusiness) {
        try {
            if (response == null) {
                return null;
            }
            Integer jobTypeCode = response.jobTypeCode();
            JobAnalysisResult.JobType jobType = JobAnalysisResult.JobType.fromCode(jobTypeCode);

            int technicalBasic = response.technicalBasicCount() == null ? 3 : response.technicalBasicCount();
            int project = response.projectCount() == null ? 3 : response.projectCount();
            int business = response.businessCount() == null ? 2 : response.businessCount();
            int softSkill = response.softSkillCount() == null ? 2 : response.softSkillCount();

            // 超限兜底：如果 LLM 返回超限，按比例缩放
            int techTotal = technicalBasic + project;
            int businessTotal = business + softSkill;

            if (techTotal > maxTechnical) {
                log.warn("技术轮题目超限: {} > {}, 按比例缩放", techTotal, maxTechnical);
                technicalBasic = scaleCount(technicalBasic, techTotal, maxTechnical);
                project = scaleCount(project, techTotal, maxTechnical);
                // 确保总数不超过限制
                int adjustedTechTotal = technicalBasic + project;
                if (adjustedTechTotal > maxTechnical) {
                    project = maxTechnical - technicalBasic;
                }
            }

            if (businessTotal > maxBusiness) {
                log.warn("业务轮题目超限: {} > {}, 按比例缩放", businessTotal, maxBusiness);
                business = scaleCount(business, businessTotal, maxBusiness);
                softSkill = scaleCount(softSkill, businessTotal, maxBusiness);
                // 确保总数不超过限制
                int adjustedBusinessTotal = business + softSkill;
                if (adjustedBusinessTotal > maxBusiness) {
                    softSkill = maxBusiness - business;
                }
            }

            int totalQuestions = (technicalBasic + project) + (business + softSkill);

            return JobAnalysisResult.builder()
                    .jobType(jobType)
                    .technicalBasicCount(technicalBasic)
                    .projectCount(project)
                    .businessCount(business)
                    .softSkillCount(softSkill)
                    .totalQuestions(totalQuestions)
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
     * 优雅兜底：如果 LLM 返回的总数超过限制，按比例缩放
     * 如果符合限制，直接返回（零开销）
     */
    private int scaleCount(int count, int totalCount, int maxCount) {
        return totalCount <= maxCount ? count : (int) Math.ceil((double) count / totalCount * maxCount);
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