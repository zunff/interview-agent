# AI 面试官系统

基于 Spring AI Alibaba + LangGraph4j 构建的多模态 AI 面试系统，支持实时视频分析、语音评估和智能追问。

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 支持 Virtual Threads |
| Spring Boot | 3.4.4 | 基础框架 |
| Spring AI | 1.0.0 | Spring 官方 AI 框架 |
| Spring AI Alibaba | 1.0.0.2 | DashScope 原生集成（Chat/Vision/ASR） |
| LangGraph4j | 1.8.11 | 有状态多步骤工作流引擎 |
| PostgreSQL + pgvector | - | 数据库与向量存储 |
| MyBatis-Plus | 3.5.9 | ORM 框架 |

| 用途 | 说明 |
|------|------|
| 大语言模型 | 面试问题生成、答案评估、情感分析 |
| 视觉模型 | 视频帧表情识别、肢体语言分析 |
| 语音模型 | 实时 ASR 语音转文字 |

## 核心功能

- **智能面试流程**: 根据简历和岗位自动生成针对性问题，支持技术基础、项目经验、业务理解、软技能等多维度考察
- **多分支追问策略**: 根据回答质量动态选择追问策略（普通追问/低分深入/高分挑战）
- **并行多模态评估**: 视觉分析（表情/肢体语言）与音频分析（语调情感/流畅度）并行执行
- **实时交互**: WebSocket 实时推送问题，缓存视频帧和音频流，回答完成后批量分析

## 整体架构

系统采用 **LangGraph4j 主图 + 子图** 架构，将面试流程建模为有向状态图。主图管理整体面试流程（岗位分析→技术轮→业务轮→报告），每个轮次通过可复用的子图实例执行。

```mermaid
graph TD
    START((START))

    subgraph 主图["InterviewAgentGraph"]
        direction TD
        INIT[InitInterviewNode<br/>初始化面试] --> JOB[JobAnalysisNode<br/>岗位分析]
        JOB --> TECH[InterviewRoundGraph<br/>技术轮子图]

        TECH --> ROUTER{RoundTransitionNode<br/>轮次决策}

        ROUTER -->|技术轮未完成| TECH
        ROUTER -->|切换业务轮| BIZ[InterviewRoundGraph<br/>业务轮子图]
        ROUTER -->|提前结束| REPORT

        BIZ --> ROUTER
        ROUTER -->|业务完成| REPORT[ReportGeneratorNode<br/>生成报告]
    end

    START --> INIT
    REPORT --> END((END))
```

### 子图架构（并行多模态分析 + 多分支追问）

```mermaid
graph TD
    SUB_START((START))

    subgraph 子图["InterviewRoundGraph"]
        direction TD
        GEN[QuestionGeneratorNode<br/>生成问题] --> ASK[AskQuestionNode<br/>记录问题]

        ASK --> WAIT[WaitForAnswerNode<br/>等待回答]

        WAIT --> VISION[VisionAnalysisNode<br/>视觉分析]
        WAIT --> AUDIO[AudioAnalysisNode<br/>音频分析]

        VISION --> AGG[AggregateAnalysisNode<br/>聚合评估]
        AUDIO --> AGG

        AGG --> DECISION{FollowUpDecisionNode<br/>追问决策}

        DECISION -->|得分 < 50| DEEP[DeepDiveNode<br/>深入追问]
        DECISION -->|得分 > 90| CHALLENGE[ChallengeQuestionNode<br/>挑战问题]
        DECISION -->|普通追问| FOLLOW[GenerateFollowUpNode<br/>生成追问]
        DECISION -->|下一题| SUB_END((END))

        DEEP --> ASK
        CHALLENGE --> ASK
        FOLLOW --> ASK
    end

    SUB_START --> GEN
```

### 关键架构特性

| 特性 | 说明 |
|------|------|
| **并行分析** | 视觉分析和音频分析同时执行，减少评估延迟 |
| **多分支路由** | 根据得分动态选择追问策略（低分深入/高分挑战/普通追问） |
| **条件聚合** | 多个分析节点完成后汇聚到聚合节点 |

### 路由决策逻辑

**追问路由（EvaluationRouter）**：

| 得分范围 | 决策 | 说明 |
|---------|------|------|
| < 50 | DEEP_DIVE | 低分深入追问，确认是否真的存在不足 |
| > 90 | CHALLENGE_MODE | 高分挑战模式，提出更有挑战性的问题 |
| 50-90 且需追问 | FOLLOW_UP | 普通追问，深入挖掘 |
| 其他 | NEXT_QUESTION | 进入下一题 |

**轮次切换路由（RoundTransitionRouter）**：

| 条件 | 转换 |
|------|------|
| 技术轮完成 && 平均分 ≥ 75 | 技术轮 → 业务轮 |
| 技术轮完成 && 平均分 < 75 | 继续技术轮 |
| 业务轮完成 | 生成报告 |
| 连续3次高分 (≥85分) | 提前结束 |

### 多模态分析流水线

```mermaid
graph LR
    VIDEO["视频帧"] --> VISION["视觉模型"] --> SCORE1["表情/肢体语言评分"]
    AUDIO["音频数据"] --> ASR["语音模型转录"] --> LLM["大语言模型"] --> SCORE2["语调情感分析"]
    SCORE1 --> AGG["聚合评估"]
    SCORE2 --> AGG
```

### 熔断与容错

- **LLM 熔断**: 连续失败 3 次自动终止图执行，`CircuitBreakerHelper` 统一管理
- **重试策略**: 最大重试 3 次，指数退避（1s→2s→4s），仅对 429/5xx 重试
- **递归限制**: 可配置，防止图执行死循环
- **节点兜底**: 各节点 catch 异常后返回默认值并递增失败计数

## 前后端交互

系统采用 **REST API + WebSocket** 双通道通信：REST 处理核心流程控制，WebSocket 处理实时数据传输（视频帧缓存、音频流缓存）。

### REST API

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/interview/start` | POST | 开始面试 |
| `/api/interview/answer` | POST | 提交答案 |
| `/api/interview/session/{sessionId}` | GET | 获取会话状态 |
| `/api/interview/end/{sessionId}` | POST | 结束面试 |
| `/api/interview/report/{sessionId}` | GET | 获取面试报告 |
| `/api/health` | GET | 健康检查 |
| `/api/info` | GET | 服务信息 |

### WebSocket

详细的 WebSocket 通信文档请查看：[WebSocket 通信文档](./docs/websocket.md)

#### 消息时序图

```mermaid
sequenceDiagram
    participant C as 客户端
    participant S as 服务端

    C->>S: WebSocket 连接 ws://localhost:8080/ws/interview/{sessionId}
    S->>C: new_question (第1题)
    S->>C: audio_question_start (语音问题开始)
    
    loop 语音问题传输
        S->>C: audio_question_chunk (音频块)
    end
    
    S->>C: audio_question_end (语音问题结束)
    
    loop 回答过程
        C->>S: video_frame (缓存)
        C->>S: audio_chunk (缓存)
    end
    
    C->>S: answer_complete
    S->>C: answer_received
    S->>S: 多模态分析（视觉+音频并行）
    S->>C: evaluation_result
    S->>C: new_question (下一题或追问)
    S->>C: audio_question_start (下一题语音开始)
    
    ... 重复直到面试结束 ...
    
    S->>C: final_report
    C->>S: WebSocket 断开
```

## 项目结构

```
src/main/java/com/zunff/interview/
├── agent/
│   ├── graph/                           # LangGraph4j 图定义
│   │   ├── InterviewAgentGraph.java     # 主图（岗位分析→技术轮→业务轮→报告）
│   │   └── InterviewRoundGraph.java     # 轮次子图（并行分析+多分支追问）
│   ├── nodes/                           # 图节点
│   │   ├── VisionAnalysisNode.java      # 视觉分析节点
│   │   ├── AudioAnalysisNode.java       # 音频分析节点
│   │   ├── AggregateAnalysisNode.java   # 聚合评估节点
│   │   ├── ChallengeQuestionNode.java   # 高分挑战节点
│   │   ├── DeepDiveNode.java            # 低分深入追问节点
│   │   └── ...
│   └── router/                          # 路由决策
│       ├── EvaluationRouter.java        # 追问路由（多分支）
│       └── RoundTransitionRouter.java   # 轮次切换路由
├── config/                              # 配置类
├── controller/                          # REST 控制器
├── model/                               # 数据模型
├── service/                             # 业务服务
├── state/
│   └── InterviewState.java              # 面试状态定义
└── websocket/                           # WebSocket 处理
```

## 快速开始

**前置要求**: JDK 21+、Maven 3.9+、PostgreSQL + pgvector、DashScope API Key

1. 复制 `application-example.yml` 为 `application-dev.yml` 并填写配置
2. 运行: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`

访问端点:
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## 配置说明

```yaml
interview:
  session:
    max-technical-questions: 6   # 技术轮最大问题数
    max-business-questions: 4    # 业务轮最大问题数
    round-pass-score: 75         # 轮次通过分数阈值
    high-score-threshold: 85     # 高分阈值（触发挑战模式）
    consecutive-high-for-early-end: 3  # 连续高分次数触发提前结束
    max-follow-ups-technical: 3  # 技术轮每题最大追问数
    max-follow-ups-business: 2   # 业务轮每题最大追问数
```

## 参考资料

- [LangGraph4j 官方文档](https://langgraph4j.github.io/langgraph4j/)
- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Alibaba 官方文档](https://java2ai.com/)
- [通义千问 API 文档](https://help.aliyun.com/zh/dashscope/)

## License

MIT License
