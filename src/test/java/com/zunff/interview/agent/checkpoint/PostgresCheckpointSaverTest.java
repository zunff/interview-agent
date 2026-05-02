package com.zunff.interview.agent.checkpoint;

import com.zunff.interview.model.bo.JobAnalysisResult;
import com.zunff.interview.model.entity.GraphCheckpoint;
import com.zunff.interview.service.GraphCheckpointService;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostgresCheckpointSaver 集成测试
 * 验证 MyBatis-Plus 重构后的 checkpoint 持久化正确性
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostgresCheckpointSaverTest {

    @Autowired
    private PostgresCheckpointSaver checkpointSaver;

    @Autowired
    private GraphCheckpointService checkpointService;

    private static final String TEST_THREAD_ID = "test-checkpoint-thread-001";

    @BeforeEach
    void setUp() {
        // 清理测试数据
        checkpointService.deleteByThreadId(TEST_THREAD_ID);
    }

    @Test
    @Order(1)
    void testSaveAndLoadCheckpoint() throws Exception {
        // 1. 创建 checkpoint
        Map<String, Object> state = new HashMap<>();
        state.put("sessionId", TEST_THREAD_ID);
        state.put("resume", "测试简历内容");
        state.put("jobInfo", "Java开发工程师");
        state.put("followUpCount", 2);

        Checkpoint checkpoint = Checkpoint.builder()
                .id("checkpoint-001")
                .nodeId("ASK_QUESTION_TECH_1")
                .nextNodeId("WAIT_FOR_ANSWER")
                .state(state)
                .build();

        // 2. 保存 checkpoint
        RunnableConfig config = RunnableConfig.builder()
                .threadId(TEST_THREAD_ID)
                .build();

        LinkedList<Checkpoint> list = new LinkedList<>();
        checkpointSaver.insertedCheckpoint(config, list, checkpoint);

        // 3. 从 DB 加载
        LinkedList<Checkpoint> loaded = checkpointSaver.loadCheckpoints(config);

        // 4. 验证
        assertFalse(loaded.isEmpty(), "Checkpoint 应该被成功加载");
        assertEquals(1, loaded.size());

        Checkpoint loadedCheckpoint = loaded.getFirst();
        assertEquals("checkpoint-001", loadedCheckpoint.getId());
        assertEquals("ASK_QUESTION_TECH_1", loadedCheckpoint.getNodeId());
        assertEquals("WAIT_FOR_ANSWER", loadedCheckpoint.getNextNodeId());

        // 验证 state 反序列化
        Map<String, Object> loadedState = loadedCheckpoint.getState();
        assertNotNull(loadedState);
        assertEquals(TEST_THREAD_ID, loadedState.get("sessionId"));
        assertEquals("测试简历内容", loadedState.get("resume"));
        assertEquals("Java开发工程师", loadedState.get("jobInfo"));
        assertEquals(2, loadedState.get("followUpCount"));
    }

    @Test
    @Order(2)
    void testUpdateCheckpoint() throws Exception {
        RunnableConfig config = RunnableConfig.builder()
                .threadId(TEST_THREAD_ID)
                .build();

        // 1. 初始保存
        Map<String, Object> state1 = new HashMap<>();
        state1.put("sessionId", TEST_THREAD_ID);
        state1.put("followUpCount", 1);

        Checkpoint checkpoint1 = Checkpoint.builder()
                .id("checkpoint-001")
                .nodeId("NODE_A")
                .nextNodeId("NODE_B")
                .state(state1)
                .build();

        LinkedList<Checkpoint> list = new LinkedList<>();
        checkpointSaver.insertedCheckpoint(config, list, checkpoint1);

        // 2. 更新 checkpoint
        Map<String, Object> state2 = new HashMap<>();
        state2.put("sessionId", TEST_THREAD_ID);
        state2.put("followUpCount", 3);
        state2.put("answerText", "用户回答内容");

        Checkpoint checkpoint2 = Checkpoint.builder()
                .id("checkpoint-002")
                .nodeId("NODE_B")
                .nextNodeId("NODE_C")
                .state(state2)
                .build();

        checkpointSaver.updatedCheckpoint(config, list, checkpoint2);

        // 3. 验证更新（每个 threadId 只保留一条记录）
        LinkedList<Checkpoint> loaded = checkpointSaver.loadCheckpoints(config);
        assertEquals(1, loaded.size(), "每个 threadId 应只保留一条记录");

        Checkpoint updated = loaded.getFirst();
        assertEquals("checkpoint-002", updated.getId());
        assertEquals("NODE_B", updated.getNodeId());
        assertEquals(3, updated.getState().get("followUpCount"));
        assertEquals("用户回答内容", updated.getState().get("answerText"));
    }

    @Test
    @Order(3)
    void testGetLatestCheckpoint() throws Exception {
        // 1. 保存第一个 checkpoint
        Map<String, Object> state1 = new HashMap<>();
        state1.put("sessionId", TEST_THREAD_ID);
        state1.put("stage", "technical");

        Checkpoint checkpoint1 = Checkpoint.builder()
                .id("checkpoint-old")
                .nodeId("NODE_1")
                .nextNodeId("NODE_2")
                .state(state1)
                .build();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(TEST_THREAD_ID)
                .build();

        LinkedList<Checkpoint> list = new LinkedList<>();
        checkpointSaver.insertedCheckpoint(config, list, checkpoint1);

        // 2. 保存第二个 checkpoint（更新）
        Map<String, Object> state2 = new HashMap<>();
        state2.put("sessionId", TEST_THREAD_ID);
        state2.put("stage", "business");

        Checkpoint checkpoint2 = Checkpoint.builder()
                .id("checkpoint-new")
                .nodeId("NODE_2")
                .nextNodeId("NODE_3")
                .state(state2)
                .build();

        checkpointSaver.updatedCheckpoint(config, list, checkpoint2);

        // 3. 获取最新 checkpoint
        Optional<Checkpoint> latest = checkpointSaver.getLatestCheckpoint(TEST_THREAD_ID);

        assertTrue(latest.isPresent(), "应该找到最新的 checkpoint");
        assertEquals("checkpoint-new", latest.get().getId());
        assertEquals("NODE_2", latest.get().getNodeId());
        assertEquals("business", latest.get().getState().get("stage"));
    }

    @Test
    @Order(4)
    void testDeleteCheckpoints() throws Exception {
        // 1. 保存 checkpoint
        Map<String, Object> state = new HashMap<>();
        state.put("sessionId", TEST_THREAD_ID);

        Checkpoint checkpoint = Checkpoint.builder()
                .id("checkpoint-to-delete")
                .nodeId("NODE_X")
                .nextNodeId("NODE_Y")
                .state(state)
                .build();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(TEST_THREAD_ID)
                .build();

        LinkedList<Checkpoint> list = new LinkedList<>();
        checkpointSaver.insertedCheckpoint(config, list, checkpoint);

        // 2. 删除
        checkpointSaver.deleteCheckpoints(TEST_THREAD_ID);

        // 3. 验证已删除
        LinkedList<Checkpoint> loaded = checkpointSaver.loadCheckpoints(config);
        assertTrue(loaded.isEmpty(), "删除后应无 checkpoint");

        Optional<Checkpoint> latest = checkpointSaver.getLatestCheckpoint(TEST_THREAD_ID);
        assertFalse(latest.isPresent(), "删除后应无法获取最新 checkpoint");
    }

    @Test
    @Order(5)
    void testJsonbMapTypeHandler() {
        // 测试 JsonbMapTypeHandler 对复杂嵌套 Map 的序列化
        Map<String, Object> complexState = new HashMap<>();
        complexState.put("sessionId", TEST_THREAD_ID);

        // 嵌套 Map
        Map<String, Object> nestedMap = new HashMap<>();
        nestedMap.put("key1", "value1");
        nestedMap.put("key2", 123);
        complexState.put("nestedData", nestedMap);

        // List
        List<String> listData = Arrays.asList("item1", "item2", "item3");
        complexState.put("listData", listData);

        // Boolean
        complexState.put("flag", true);

        // 保存
        GraphCheckpoint entity = GraphCheckpoint.builder()
                .checkpointId("complex-checkpoint")
                .nodeId("COMPLEX_NODE")
                .state(complexState)
                .build();

        checkpointService.saveOrUpdateCheckpoint(TEST_THREAD_ID, entity);

        // 加载并验证
        Optional<GraphCheckpoint> loaded = checkpointService.getLatestByThreadId(TEST_THREAD_ID);
        assertTrue(loaded.isPresent());

        Map<String, Object> loadedState = loaded.get().getState();
        assertEquals(TEST_THREAD_ID, loadedState.get("sessionId"));

        // 验证嵌套 Map
        @SuppressWarnings("unchecked")
        Map<String, Object> loadedNested = (Map<String, Object>) loadedState.get("nestedData");
        assertEquals("value1", loadedNested.get("key1"));
        assertEquals(123, loadedNested.get("key2"));

        // 验证 List
        @SuppressWarnings("unchecked")
        List<String> loadedList = (List<String>) loadedState.get("listData");
        assertEquals(3, loadedList.size());
        assertEquals("item1", loadedList.get(0));

        // 验证 Boolean
        assertEquals(true, loadedState.get("flag"));
    }

    @Test
    @Order(6)
    void testLoadNonExistentCheckpoint() throws Exception {
        RunnableConfig config = RunnableConfig.builder()
                .threadId("non-existent-thread-id")
                .build();

        LinkedList<Checkpoint> loaded = checkpointSaver.loadCheckpoints(config);
        assertTrue(loaded.isEmpty(), "不存在的 threadId 应返回空列表");

        Optional<Checkpoint> latest = checkpointSaver.getLatestCheckpoint("non-existent-thread-id");
        assertFalse(latest.isPresent(), "不存在的 threadId 应返回 Optional.empty()");
    }

    @Test
    @Order(7)
    void testComplexObjectSerialization() throws Exception {
        // 测试包含业务对象的 state 序列化/反序列化
        // 这个测试暴露类型转换的 bug
        Map<String, Object> state = new HashMap<>();
        state.put("sessionId", TEST_THREAD_ID);

        // 创建业务对象
        JobAnalysisResult jobAnalysisResult = JobAnalysisResult.builder()
                .jobType(JobAnalysisResult.JobType.TECHNICAL_DRIVEN)
                .positionLevel(JobAnalysisResult.PositionLevel.SENIOR)
                .technicalBasicCount(3)
                .projectCount(2)
                .businessCount(2)
                .softSkillCount(1)
                .totalQuestions(8)
                .keyRequirements("Java, Spring Boot, 微服务")
                .techStackSummary("Java, Spring Boot, Spring Cloud, MySQL, Redis")
                .businessDomain("电商系统")
                .build();

        state.put("jobAnalysisResult", jobAnalysisResult);

        Checkpoint checkpoint = Checkpoint.builder()
                .id("checkpoint-with-complex-object")
                .nodeId("JOB_ANALYSIS")
                .nextNodeId("TECHNICAL_ROUND")
                .state(state)
                .build();

        RunnableConfig config = RunnableConfig.builder()
                .threadId(TEST_THREAD_ID)
                .build();

        LinkedList<Checkpoint> list = new LinkedList<>();
        checkpointSaver.insertedCheckpoint(config, list, checkpoint);

        // 从 DB 加载
        LinkedList<Checkpoint> loaded = checkpointSaver.loadCheckpoints(config);
        assertFalse(loaded.isEmpty());

        Checkpoint loadedCheckpoint = loaded.getFirst();
        Map<String, Object> loadedState = loadedCheckpoint.getState();

        // 这里会失败：loadedState.get("jobAnalysisResult") 返回的是 LinkedHashMap，不是 JobAnalysisResult
        Object resultObj = loadedState.get("jobAnalysisResult");
        assertNotNull(resultObj, "jobAnalysisResult 不应为 null");

        // 尝试类型转换 - 这里会抛出 ClassCastException
        assertDoesNotThrow(() -> {
            JobAnalysisResult loadedResult = (JobAnalysisResult) resultObj;
            assertEquals(JobAnalysisResult.JobType.TECHNICAL_DRIVEN, loadedResult.getJobType());
            assertEquals(JobAnalysisResult.PositionLevel.SENIOR, loadedResult.getPositionLevel());
            assertEquals(8, loadedResult.getTotalQuestions());
        }, "jobAnalysisResult 应该能正确反序列化为 JobAnalysisResult 类型");
    }
}
