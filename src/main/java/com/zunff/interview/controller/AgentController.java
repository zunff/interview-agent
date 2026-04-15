package com.zunff.interview.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统健康检查和状态控制器
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "系统管理", description = "系统健康检查和状态接口")
public class AgentController {

    private final ChatModel chatModel;
    private final ChatClient textChatClient;

    /**
     * 健康检查
     */
    @Operation(summary = "健康检查", description = "检查服务是否正常运行")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "interview-agent",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 获取服务信息
     */
    @Operation(summary = "获取服务信息", description = "获取服务的基本信息和功能列表")
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
                "name", "Interview Agent Service",
                "version", "1.0.0",
                "description", "基于 Spring AI + LangGraph4j 的实时视频面试系统",
                "features", new String[]{
                        "实时视频面试",
                        "多模态分析（视频/音频/文本）",
                        "智能问题生成与追问",
                        "综合评估报告"
                }
        ));
    }

    /**
     * 测试 ChatModel 调用
     */
    @Operation(summary = "测试 ChatModel", description = "测试 ChatModel 直接调用")
    @GetMapping("/test/chat-model")
    public ResponseEntity<Map<String, Object>> testChatModel(@RequestParam(defaultValue = "你好") String message) {
        log.info("测试 ChatModel，消息: {}", message);
        try {
            String response = chatModel.call(message);
            log.info("ChatModel 响应成功，长度: {}", response.length());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "model", chatModel.getDefaultOptions().getModel(),
                    "message", message,
                    "response", response
            ));
        } catch (Exception e) {
            log.error("ChatModel 调用失败", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "errorClass", e.getClass().getSimpleName()
            ));
        }
    }

    /**
     * 测试 ChatClient 调用
     */
    @Operation(summary = "测试 ChatClient", description = "测试 ChatClient 流式调用")
    @GetMapping("/test/chat-client")
    public ResponseEntity<Map<String, Object>> testChatClient(@RequestParam(defaultValue = "你好") String message) {
        log.info("测试 ChatClient，消息: {}", message);
        try {
            String response = textChatClient.prompt()
                    .user(message)
                    .call()
                    .content();
            log.info("ChatClient 响应成功，长度: {}", response.length());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", message,
                    "response", response
            ));
        } catch (Exception e) {
            log.error("ChatClient 调用失败", e);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "errorClass", e.getClass().getSimpleName()
            ));
        }
    }
}