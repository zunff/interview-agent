package com.zunff.interview.agent.checkpoint;

import com.zunff.interview.model.entity.GraphCheckpoint;
import com.zunff.interview.service.GraphCheckpointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.AbstractCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver.Tag;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * PostgreSQL 持久化 CheckpointSaver
 * 替代 LangGraph4j 的 MemorySaver，将图状态持久化到 PostgreSQL。
 * 支持服务重启后从 DB 恢复 checkpoint，实现断连重连。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostgresCheckpointSaver extends AbstractCheckpointSaver {

    private final GraphCheckpointService checkpointService;

    @Override
    protected LinkedList<Checkpoint> loadCheckpoints(RunnableConfig config) throws Exception {
        String threadId = threadId(config);

        List<GraphCheckpoint> entities = checkpointService.getByThreadId(threadId);

        LinkedList<Checkpoint> checkpoints = entities.stream()
                .map(this::toCheckpoint)
                .collect(Collectors.toCollection(LinkedList::new));

        log.debug("从 DB 加载 checkpoint: threadId={}, count={}", threadId, checkpoints.size());
        return checkpoints;
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> list, Checkpoint checkpoint)
            throws Exception {
        String threadId = threadId(config);
        GraphCheckpoint entity = toEntity(checkpoint);

        checkpointService.saveOrUpdateCheckpoint(threadId, entity);
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> list, Checkpoint checkpoint)
            throws Exception {
        String threadId = threadId(config);
        GraphCheckpoint entity = toEntity(checkpoint);

        checkpointService.saveOrUpdateCheckpoint(threadId, entity);
    }

    @Override
    protected Tag releaseCheckpoints(RunnableConfig config, LinkedList<Checkpoint> list) throws Exception {
        String threadId = threadId(config);

        // 不删除 checkpoint，保留用于重连
        log.debug("释放 checkpoint（保留）: threadId={}, count={}", threadId, list.size());
        return new Tag(threadId, list);
    }

    /**
     * 删除指定 threadId 的所有 checkpoint（面试结束时调用）
     */
    public void deleteCheckpoints(String threadId) {
        checkpointService.deleteByThreadId(threadId);
    }

    /**
     * 获取指定 threadId 的最新 checkpoint
     */
    public Optional<Checkpoint> getLatestCheckpoint(String threadId) {
        try {
            Optional<GraphCheckpoint> entityOpt = checkpointService.getLatestByThreadId(threadId);
            return entityOpt.map(this::toCheckpoint);
        } catch (Exception e) {
            log.error("获取最新 checkpoint 失败: threadId={}", threadId, e);
            return Optional.empty();
        }
    }

    // ========== 转换方法 ==========

    private Checkpoint toCheckpoint(GraphCheckpoint entity) {
        return Checkpoint.builder()
                .id(entity.getCheckpointId())
                .state(entity.getState())  // MyBatis-Plus 已自动反序列化
                .nodeId(entity.getNodeId())
                .nextNodeId(entity.getNextNodeId())
                .build();
    }

    private GraphCheckpoint toEntity(Checkpoint checkpoint) {
        return GraphCheckpoint.builder()
                .checkpointId(checkpoint.getId())
                .nodeId(checkpoint.getNodeId())
                .nextNodeId(checkpoint.getNextNodeId())
                .state(checkpoint.getState())
                .build();
    }
}