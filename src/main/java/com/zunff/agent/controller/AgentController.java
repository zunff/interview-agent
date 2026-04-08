package com.zunff.agent.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统健康检查和状态控制器
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AgentController {

    /**
     * 健康检查
     */
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
}