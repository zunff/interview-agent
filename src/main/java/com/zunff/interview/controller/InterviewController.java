package com.zunff.interview.controller;

import com.zunff.interview.common.response.ApiResponse;
import com.zunff.interview.model.request.StartInterviewRequest;
import com.zunff.interview.model.request.SubmitAnswerRequest;
import com.zunff.interview.model.response.InterviewAnswerResponse;
import com.zunff.interview.model.response.InterviewStartResponse;
import com.zunff.interview.model.response.ReportResponse;
import com.zunff.interview.model.response.SessionResponse;
import com.zunff.interview.service.interview.InterviewBusinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 面试控制器
 * 提供面试相关的 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
@Tag(name = "面试管理", description = "面试流程相关接口")
public class InterviewController {

    private final InterviewBusinessService interviewBusinessService;

    @Operation(summary = "开始面试", description = "根据简历和岗位信息创建新的面试会话")
    @PostMapping("/start")
    public ApiResponse<InterviewStartResponse> startInterview(@Valid @RequestBody StartInterviewRequest request) {
        log.info("开始面试，岗位: {}", request.getJobInfo());
        InterviewStartResponse response = interviewBusinessService.startInterview(request);
        return ApiResponse.success(response);
    }


    @Operation(summary = "【测试用】提交答案", description = "测试时通过 HTTP 提交文本答案，生产环境使用 WebSocket 的 answer_complete 信号")
    @PostMapping("/answer")
    public ApiResponse<InterviewAnswerResponse> submitAnswer(@Valid @RequestBody SubmitAnswerRequest request) {
        log.info("提交答案，会话: {}", request.getSessionId());
        InterviewAnswerResponse response = interviewBusinessService.submitAnswer(request);
        return ApiResponse.success(response);
    }


    @Operation(summary = "获取会话状态", description = "获取当前面试会话的状态信息")
    @GetMapping("/session/{sessionId}")
    public ApiResponse<SessionResponse> getSession(
            @Parameter(description = "面试会话ID", required = true) @PathVariable String sessionId) {
        SessionResponse response = interviewBusinessService.getSessionStatus(sessionId);
        return ApiResponse.success(response);
    }


    @Operation(summary = "结束面试", description = "结束当前面试会话并生成评估报告")
    @PostMapping("/end/{sessionId}")
    public ApiResponse<ReportResponse> endInterview(
            @Parameter(description = "面试会话ID", required = true) @PathVariable String sessionId) {
        log.info("结束面试，会话: {}", sessionId);
        ReportResponse response = interviewBusinessService.endInterview(sessionId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "获取面试报告", description = "获取已完成面试的评估报告")
    @GetMapping("/report/{sessionId}")
    public ApiResponse<ReportResponse> getReport(
            @Parameter(description = "面试会话ID", required = true) @PathVariable String sessionId) {
        ReportResponse response = interviewBusinessService.getReport(sessionId);
        return ApiResponse.success(response);
    }
}
