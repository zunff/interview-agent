package com.zunff.interview.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zunff.interview.model.entity.GraphCheckpoint;

import java.util.List;
import java.util.Optional;

/**
 * GraphCheckpoint Service 接口
 */
public interface GraphCheckpointService extends IService<GraphCheckpoint> {

    /**
     * 按 threadId 获取所有 checkpoint（按创建时间升序）
     */
    List<GraphCheckpoint> getByThreadId(String threadId);

    /**
     * 获取指定 threadId 的最新（最后一条）checkpoint
     */
    Optional<GraphCheckpoint> getLatestByThreadId(String threadId);

    /**
     * 按 threadId 删除所有 checkpoint
     */
    void deleteByThreadId(String threadId);

    /**
     * 保存或更新 checkpoint（每个 threadId 只保留一条）
     */
    void saveOrUpdateCheckpoint(String threadId, GraphCheckpoint checkpoint);
}