package com.zunff.interview.controller;

import com.zunff.interview.common.response.ApiResponse;
import com.zunff.interview.common.response.PageResult;
import com.zunff.interview.model.response.InterviewHistoryResponse;
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

    @Operation(summary = "获取面试历史列表", description = "分页获取面试历史记录，支持岗位信息搜索，按创建时间倒序排列")
    @GetMapping("/history")
    public ApiResponse<PageResult<InterviewHistoryResponse>> listHistory(
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键词（岗位信息模糊匹配）") @RequestParam(required = false) String keyword) {
        PageResult<InterviewHistoryResponse> result = interviewBusinessService.listSessions(page, size, keyword);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取面试报告", description = "获取已完成面试的评估报告")
    @GetMapping("/report/{sessionId}")
    public ApiResponse<ReportResponse> getReport(
            @Parameter(description = "面试会话ID", required = true) @PathVariable String sessionId) {
        ReportResponse response = interviewBusinessService.getReport(sessionId);
        return ApiResponse.success(response);
    }
}
