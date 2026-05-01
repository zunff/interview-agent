package com.zunff.interview.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zunff.interview.mapper.ChatSessionMapper;
import com.zunff.interview.mapper.ResumeAnalysisMapper;
import com.zunff.interview.model.entity.ChatSession;
import com.zunff.interview.model.entity.ResumeAnalysis;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChatServiceIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ResumeAnalysisMapper resumeAnalysisMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String testSessionId;

    @Test
    @Order(1)
    void testUploadResumeAndVerifyReportCachedInChatMemory() {
        // 创建会话
        testSessionId = chatService.createSession().getSessionId();
        assertNotNull(testSessionId);

        // 使用真实的简历文件
        MultipartFile mockFile = createMockPdfFile("简历 - 第10 版.docx");

        // 上传简历
        SseEmitter emitter = chatService.uploadResume(testSessionId, mockFile, null);
        assertNotNull(emitter);

        // 等待异步执行完成（增加超时时间到90秒）
        // 注意：需要同时等待 ResumeAnalysis 完成 AND ChatMemory 记录被创建
        await().atMost(90, TimeUnit.SECONDS).until(() -> {
            ResumeAnalysis analysis = resumeAnalysisMapper.selectOne(
                new LambdaQueryWrapper<ResumeAnalysis>()
                    .eq(ResumeAnalysis::getSessionId, testSessionId)
            );

            if (analysis == null || !"DONE".equals(analysis.getStatus())) {
                return false;
            }

            // 额外等待 ChatMemory 记录（chatMemory.add() 在图完成后执行）
            List<Map<String, Object>> memoryRecords = jdbcTemplate.queryForList(
                "SELECT COUNT(*) as count FROM spring_ai_chat_memory WHERE conversation_id = ?",
                testSessionId
            );
            int count = ((Number) memoryRecords.get(0).get("count")).intValue();
            return count > 0;
        });

        // 验证数据库记录
        ResumeAnalysis analysis = resumeAnalysisMapper.selectOne(
            new LambdaQueryWrapper<ResumeAnalysis>()
                .eq(ResumeAnalysis::getSessionId, testSessionId)
        );

        assertNotNull(analysis, "ResumeAnalysis 记录应该存在");
        assertEquals("DONE", analysis.getStatus(), "状态应为DONE");
        assertNotNull(analysis.getReportMarkdown(), "报告内容不应为空");
        assertNotNull(analysis.getRadarChartData(), "雷达图数据不应为空");

        // 验证 spring_ai_chat_memory 表（验证 ChatMemory 缓存）
        List<Map<String, Object>> memoryRecords = jdbcTemplate.queryForList(
            "SELECT * FROM spring_ai_chat_memory WHERE conversation_id = ? ORDER BY timestamp ASC",
            testSessionId
        );

        assertNotNull(memoryRecords, "ChatMemory 记录不应为空");
        assertTrue(!memoryRecords.isEmpty(), "ChatMemory 应有记录");

        // 验证包含系统消息（report）
        boolean hasSystemMessage = memoryRecords.stream()
            .anyMatch(record -> "SYSTEM".equals(record.get("type"))
                && record.get("content").toString().contains("简历分析报告"));
        assertTrue(hasSystemMessage, "spring_ai_chat_memory 应包含系统消息（report）");

        System.out.println("✅ Report 已成功缓存到 spring_ai_chat_memory，消息数: " + memoryRecords.size());
        System.out.println("✅ Report 内容预览: " + memoryRecords.stream()
            .filter(r -> "SYSTEM".equals(r.get("type")))
            .findFirst()
            .map(r -> r.get("content").toString().substring(0, 200))
            .orElse("N/A"));
    }

    @Test
    @Order(2)
    void testSendMessageUsesCachedReport() {
        // 验证 ChatMemory 中有系统消息
        List<Map<String, Object>> memoryRecordsBefore = jdbcTemplate.queryForList(
            "SELECT COUNT(*) as count FROM spring_ai_chat_memory WHERE conversation_id = ?",
            testSessionId
        );
        int countBefore = ((Number) memoryRecordsBefore.get(0).get("count")).intValue();
        assertTrue(countBefore > 0, "ChatMemory 应有初始记录");

        // 发送消息
        String message = "请总结我的核心技能";
        SseEmitter emitter = chatService.sendMessage(testSessionId, message);

        // 等待 SSE 完成
        AtomicBoolean completed = new AtomicBoolean(false);
        emitter.onCompletion(() -> completed.set(true));
        emitter.onError(e -> completed.set(true));

        await().atMost(60, TimeUnit.SECONDS).untilTrue(completed);

        // 验证 ChatMemory 中消息数量增加
        List<Map<String, Object>> memoryRecordsAfter = jdbcTemplate.queryForList(
            "SELECT COUNT(*) as count FROM spring_ai_chat_memory WHERE conversation_id = ?",
            testSessionId
        );
        int countAfter = ((Number) memoryRecordsAfter.get(0).get("count")).intValue();

        assertTrue(countAfter > countBefore,
            "ChatMemory 应增加新消息");

        System.out.println("✅ 发送消息成功，ChatMemory 已从 " + countBefore + " 条增加到 " + countAfter + " 条");
    }

    
    private MultipartFile createMockPdfFile(String filename) {
        // 使用真实的简历文件进行测试
        try {
            java.nio.file.Path testFile = java.nio.file.Path.of("/Users/zunf/code/agent/interview-agent/test", filename);
            if (java.nio.file.Files.exists(testFile)) {
                byte[] content = java.nio.file.Files.readAllBytes(testFile);
                String contentType = filename.endsWith(".docx") ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document" : "application/pdf";
                return new MockMultipartFile("file", filename, contentType, content);
            }
        } catch (Exception e) {
            // 如果文件读取失败，使用简单内容
        }
        // Fallback: 创建简单的模拟内容
        byte[] content = "Sample resume content for testing".getBytes();
        return new MockMultipartFile("file", filename, "application/pdf", content);
    }
}

