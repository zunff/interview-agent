package com.zunff.interview.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zunff.interview.config.JsonbMapTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 图状态 Checkpoint 实体
 * 用于持久化 LangGraph4j 的检查点状态，支持断连重连
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "graph_checkpoint", autoResultMap = true)
public class GraphCheckpoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 线程ID（对应业务层的 sessionId） */
    private String threadId;

    /** 检查点ID */
    private String checkpointId;

    /** 当前所在节点 */
    private String nodeId;

    /** 下一个要执行的节点 */
    private String nextNodeId;

    /** 完整图状态（序列化后的 Map<String,Object>） */
    @TableField(typeHandler = JsonbMapTypeHandler.class)
    private Map<String, Object> state;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
