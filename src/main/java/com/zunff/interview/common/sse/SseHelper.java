package com.zunff.interview.common.sse;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class SseHelper {

    public static void sendProgress(SseEmitter emitter, String node, String status) {
        send(emitter, SseEventType.PROGRESS, Map.of("node", node, "status", status));
    }

    public static void sendDimensionScore(SseEmitter emitter, String key, String name, int score, String comment) {
        send(emitter, SseEventType.DIMENSION_SCORE, Map.of(
                "key", key, "name", name, "score", score, "comment", comment
        ));
    }

    public static void sendRadarChart(SseEmitter emitter, String radarChartJson) {
        send(emitter, SseEventType.RADAR_CHART, Map.of("data", radarChartJson));
    }

    public static void sendToolStatus(SseEmitter emitter, String toolName, String state) {
        send(emitter, SseEventType.TOOL_STATUS, Map.of("tool", toolName, "state", state));
    }

    public static void sendMessage(SseEmitter emitter, String content) {
        send(emitter, SseEventType.MESSAGE, Map.of("content", content));
    }

    public static void sendDone(SseEmitter emitter) {
        if (emitter == null) {
            log.warn("SSE emitter 为 null，跳过 done 事件");
            return;
        }
        send(emitter, SseEventType.DONE, Map.of("message", "[DONE]"));
        safeComplete(emitter);
    }

    public static void sendError(SseEmitter emitter, String error) {
        if (emitter == null) {
            log.warn("SSE emitter 为 null，跳过 error 事件: {}", error);
            return;
        }
        send(emitter, SseEventType.ERROR, Map.of("error", error));
        safeCompleteWithError(emitter, error);
    }

    private static void send(SseEmitter emitter, SseEventType type, Object data) {
        if (emitter == null) {
            log.warn("SSE emitter 为 null，跳发送: type={}", type);
            return;
        }
        try {
            emitter.send(SseEmitter.event()
                    .name(type.name().toLowerCase())
                    .data(JSONUtil.toJsonStr(data)));
        } catch (IOException e) {
            log.warn("SSE 发送失败 (客户端可能已断开): type={}, error={}", type, e.getMessage());
        }
    }

    private static void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE complete 异常（可忽略）: {}", e.getMessage());
        }
    }

    private static void safeCompleteWithError(SseEmitter emitter, String error) {
        try {
            emitter.completeWithError(new RuntimeException(error));
        } catch (Exception e) {
            log.debug("SSE completeWithError 异常（可忽略）: {}", e.getMessage());
        }
    }
}
