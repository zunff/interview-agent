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

**连接地址**: `ws://localhost:8080/ws/interview/{sessionId}`

所有消息格式统一为：
```json
{
  "type": "消息类型",
  "payload": { /* 消息内容 */ },
  "timestamp": 1699999999999
}
```

#### 客户端 → 服务端

**video_frame** - 发送视频帧（缓存，answer_complete 时批量分析）

请求参数：
```json
{
  "type": "video_frame",
  "sessionId": "interview-xxx",
  "frame": "base64编码的图片数据"
}
```

说明：
- `frame`: Base64 编码的 JPEG/PNG 图片，建议每 2-3 秒发送一帧关键帧
- 视频帧会被缓存，在 `answer_complete` 时批量取出进行视觉分析

---

**audio_chunk** - 发送音频块（缓存，answer_complete 时批量分析）

请求参数：
```json
{
  "type": "audio_chunk",
  "sessionId": "interview-xxx",
  "audio": "base64编码的音频数据"
}
```

说明：
- `audio`: Base64 编码的音频数据（PCM/WAV 格式）
- 音频块会被缓存拼接，在 `answer_complete` 时取出进行语音分析

---

**answer_complete** - 回答完成信号（触发缓存数据分析和下一题生成）

请求参数：
```json
{
  "type": "answer_complete",
  "sessionId": "interview-xxx"
}
```

说明：
- 发送此信号表示候选人已完成当前问题的回答
- 服务端会取出缓存的视频帧和音频数据，进行多模态分析
- 分析完成后会推送 `evaluation_result` 和 `new_question`（或 `final_report`）

#### 服务端 → 客户端

**answer_received** - 回答已接收确认

响应格式：
```json
{
  "type": "answer_received",
  "payload": {
    "message": "回答已接收，正在评估中..."
  },
  "timestamp": 1699999999999
}
```

---

**new_question** - 推送新面试问题

响应格式：
```json
{
  "type": "new_question",
  "payload": {
    "content": "请介绍一下你在简历中提到的电商系统架构设计",
    "questionType": "技术基础",
    "questionIndex": 1,
    "isFollowUp": false
  },
  "timestamp": 1699999999999
}
```

字段说明：
| 字段 | 类型 | 说明 |
|------|------|------|
| `content` | string | 问题内容 |
| `questionType` | string | 问题类型：技术基础/项目经验/技术难点/系统设计/业务理解/场景分析/沟通协作/职业素养/追问 |
| `questionIndex` | int | 问题序号（从 1 开始） |
| `isFollowUp` | boolean | 是否为追问 |

---

**evaluation_result** - 答案评估结果

响应格式：
```json
{
  "type": "evaluation_result",
  "payload": {
    "questionIndex": 1,
    "question": "请介绍一下你在简历中提到的电商系统架构设计",
    "answer": "我之前的项目主要使用Spring Boot...",
    "accuracy": 85,
    "logic": 80,
    "fluency": 75,
    "confidence": 70,
    "emotionScore": 65,
    "bodyLanguageScore": 60,
    "voiceToneScore": 72,
    "overallScore": 73,
    "strengths": ["回答内容准确", "技术细节丰富"],
    "weaknesses": ["肢体语言略显紧张"],
    "detailedEvaluation": "候选人展示了扎实的...",
    "needFollowUp": false,
    "followUpSuggestion": null,
    "modalityFollowUpSuggestion": "肢体语言紧张，建议追问自信度",
    "modalityConcern": true
  },
  "timestamp": 1699999999999
}
```

字段说明：
| 字段 | 类型 | 说明 |
|------|------|------|
| `questionIndex` | int | 问题序号 |
| `question` | string | 问题内容 |
| `answer` | string | 回答内容（语音转写结果） |
| `accuracy` | int | 内容准确性得分 (0-100) |
| `logic` | int | 逻辑清晰度得分 (0-100) |
| `fluency` | int | 表达流畅度得分 (0-100) |
| `confidence` | int | 自信程度得分 (0-100) |
| `emotionScore` | int | 视频情感得分 (0-100) |
| `bodyLanguageScore` | int | 肢体语言得分 (0-100) |
| `voiceToneScore` | int | 语音语调得分 (0-100) |
| `overallScore` | int | 综合得分 (0-100) |
| `strengths` | string[] | 优点列表 |
| `weaknesses` | string[] | 不足列表 |
| `detailedEvaluation` | string | 详细评价 |
| `needFollowUp` | boolean | 是否需要追问（内部决策，不影响前端） |
| `modalityConcern` | boolean | 是否存在多模态异常 |

---

**final_report** - 最终面试报告

响应格式：
```json
{
  "type": "final_report",
  "payload": {
    "report": "# 面试评估报告\n\n## 综合评价\n...\n\n## 技术能力\n..."
  },
  "timestamp": 1699999999999
}
```

说明：
- `report`: Markdown 格式的完整面试评估报告
- 面试结束时推送，包含综合评价、各维度得分、问题回顾等

---

**error** - 错误消息

响应格式：
```json
{
  "type": "error",
  "payload": {
    "message": "答案处理失败: xxx"
  },
  "timestamp": 1699999999999
}
```

#### 消息时序图

```mermaid
sequenceDiagram
    participant C as 客户端
    participant S as 服务端

    C->>S: WebSocket 连接 ws://localhost:8080/ws/interview/{sessionId}
    S->>C: new_question (第1题)
    
    loop 回答过程
        C->>S: video_frame (缓存)
        C->>S: audio_chunk (缓存)
    end
    
    C->>S: answer_complete
    S->>C: answer_received
    S->>S: 多模态分析（视觉+音频并行）
    S->>C: evaluation_result
    S->>C: new_question (下一题或追问)
    
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
3. 测试: `python3 test/test_api.py`

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
