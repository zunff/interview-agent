package com.zunff.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zunff.interview.mapper.GraphCheckpointMapper;
import com.zunff.interview.model.entity.GraphCheckpoint;
import com.zunff.interview.service.GraphCheckpointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * GraphCheckpoint Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphCheckpointServiceImpl extends ServiceImpl<GraphCheckpointMapper, GraphCheckpoint>
        implements GraphCheckpointService {

    @Override
    public List<GraphCheckpoint> getByThreadId(String threadId) {
        return list(new LambdaQueryWrapper<GraphCheckpoint>()
                .eq(GraphCheckpoint::getThreadId, threadId)
                .orderByAsc(GraphCheckpoint::getCreatedAt));
    }

    @Override
    public Optional<GraphCheckpoint> getLatestByThreadId(String threadId) {
        GraphCheckpoint entity = getOne(new LambdaQueryWrapper<GraphCheckpoint>()
                .eq(GraphCheckpoint::getThreadId, threadId)
                .orderByDesc(GraphCheckpoint::getCreatedAt)
                .last("LIMIT 1"));
        return Optional.ofNullable(entity);
    }

    @Override
    @Transactional
    public void deleteByThreadId(String threadId) {
        remove(new LambdaQueryWrapper<GraphCheckpoint>()
                .eq(GraphCheckpoint::getThreadId, threadId));
        log.info("删除 checkpoint: threadId={}", threadId);
    }

    @Override
    @Transactional
    public void saveOrUpdateCheckpoint(String threadId, GraphCheckpoint checkpoint) {
        // 每个 threadId 只保留一条记录（UNIQUE 索引）
        Optional<GraphCheckpoint> existing = getLatestByThreadId(threadId);

        if (existing.isPresent()) {
            // 更新已有记录
            GraphCheckpoint toUpdate = existing.get();
            toUpdate.setCheckpointId(checkpoint.getCheckpointId());
            toUpdate.setNodeId(checkpoint.getNodeId());
            toUpdate.setNextNodeId(checkpoint.getNextNodeId());
            toUpdate.setState(checkpoint.getState());
            updateById(toUpdate);
            log.info("更新 checkpoint: threadId={}, checkpointId={}", threadId, checkpoint.getCheckpointId());
        } else {
            // 插入新记录
            checkpoint.setThreadId(threadId);
            save(checkpoint);
            log.info("插入 checkpoint: threadId={}, checkpointId={}", threadId, checkpoint.getCheckpointId());
        }
    }
}