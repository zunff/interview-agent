package com.zunff.interview.controller;

import com.zunff.interview.common.response.ApiResponse;
import com.zunff.interview.model.request.SubmitAnswerRequest;
import com.zunff.interview.model.response.InterviewAnswerResponse;
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
 *
 * 注意：面试启动已迁移到 WebSocket（start_interview 消息）
 */
@Slf4j
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
@Tag(name = "面试管理", description = "面试流程相关接口")
public class InterviewController {

    private final InterviewBusinessService interviewBusinessService;
    @Operation(summary = "获取面试报告", description = "获取已完成面试的评估报告")
    @GetMapping("/report/{sessionId}")
    public ApiResponse<ReportResponse> getReport(
            @Parameter(description = "面试会话ID", required = true) @PathVariable String sessionId) {
        ReportResponse response = interviewBusinessService.getReport(sessionId);
        return ApiResponse.success(response);
    }
}
