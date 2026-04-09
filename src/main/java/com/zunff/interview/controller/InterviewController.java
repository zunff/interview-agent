package com.zunff.interview.controller;

import com.zunff.interview.common.response.ApiResponse;
import com.zunff.interview.model.dto.request.StartInterviewRequest;
import com.zunff.interview.model.dto.request.SubmitAnswerRequest;
import com.zunff.interview.model.dto.response.InterviewAnswerResponse;
import com.zunff.interview.model.dto.response.InterviewStartResponse;
import com.zunff.interview.model.dto.response.ReportResponse;
import com.zunff.interview.model.dto.response.SessionResponse;
import com.zunff.interview.service.InterviewBusinessService;
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
public class InterviewController {

    private final InterviewBusinessService interviewBusinessService;

    /**
     * 开始面试
     */
    @PostMapping("/start")
    public ApiResponse<InterviewStartResponse> startInterview(@Valid @RequestBody StartInterviewRequest request) {
        log.info("开始面试，岗位: {}", request.getJobInfo());
        InterviewStartResponse response = interviewBusinessService.startInterview(request);
        return ApiResponse.success(response);
    }

    /**
     * 提交答案
     */
    @PostMapping("/answer")
    public ApiResponse<InterviewAnswerResponse> submitAnswer(@Valid @RequestBody SubmitAnswerRequest request) {
        log.info("提交答案，会话: {}", request.getSessionId());
        InterviewAnswerResponse response = interviewBusinessService.submitAnswer(request);
        return ApiResponse.success(response);
    }

    /**
     * 获取会话状态
     */
    @GetMapping("/session/{sessionId}")
    public ApiResponse<SessionResponse> getSession(@PathVariable String sessionId) {
        SessionResponse response = interviewBusinessService.getSessionStatus(sessionId);
        return ApiResponse.success(response);
    }

    /**
     * 结束面试
     */
    @PostMapping("/end/{sessionId}")
    public ApiResponse<ReportResponse> endInterview(@PathVariable String sessionId) {
        log.info("结束面试，会话: {}", sessionId);
        ReportResponse response = interviewBusinessService.endInterview(sessionId);
        return ApiResponse.success(response);
    }

    /**
     * 获取面试报告
     */
    @GetMapping("/report/{sessionId}")
    public ApiResponse<ReportResponse> getReport(@PathVariable String sessionId) {
        ReportResponse response = interviewBusinessService.getReport(sessionId);
        return ApiResponse.success(response);
    }
}
