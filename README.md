# Spring AI + LangGraph4j Agent 项目

基于 Java 21 的多智能体应用项目,集成了 Spring AI 和 LangGraph4j 框架。

## 技术栈

- **Java 21** - 支持 Virtual Threads 和最新特性
- **Spring Boot 3.4.4** - 基础框架
- **Spring AI 1.1.4** - Spring 官方 AI 集成框架
- **LangGraph4j 1.8.11** - 构建有状态多智能体应用的 Java 库

## 核心概念

### LangGraph4j 特性

LangGraph4j 是受 Python LangGraph 启发的 Java 库,用于构建**有状态的、多智能体应用**:

#### 1. StateGraph (状态图)
- 定义应用的结构:节点(Nodes)和边(Edges)
- 支持循环图结构,适合 Agent 架构
- 编译后生成可执行的 CompiledGraph

#### 2. AgentState (智能体状态)
- 图的共享状态,在节点间传递
- 使用 Map<String, Object> 存储
- 支持 Schema 定义和 Reducer 合并策略

#### 3. Nodes (节点)
- 执行具体动作的构建块
- 接收状态,处理逻辑,返回状态更新
- 支持同步和异步(CompletableFuture)

#### 4. Edges (边)
- 定义节点间的控制流
- **普通边**: 无条件转移
- **条件边**: 根据状态动态选择下一个节点
- 支持分支和循环

#### 5. Checkpoints (检查点)
- 保存和恢复图的状态
- 支持调试、回放和"时间旅行"
- 适合长时间运行的 Agent 交互

#### 6. Studio (可视化工具)
- Web UI 可视化、运行和调试图
- 支持 Spring Boot、Quarkus、Jetty 集成
- 实时观察图执行过程

### Spring AI + LangGraph4j 集成优势

- **Spring AI**: 提供统一的 LLM 接口、向量数据库、RAG 等功能
- **LangGraph4j**: 提供状态管理、工作流编排、多 Agent 协作能力
- **互补关系**: Spring AI 作为底层 AI 能力提供者,LangGraph4j 作为上层编排框架

## 项目结构

```
src/main/java/com/example/agent/
├── AgentApplication.java          # 主应用类
├── config/                        # 配置类
│   └── AgentConfiguration.java   # Agent 和图配置
├── state/                         # 状态定义
│   └── AgentState.java           # 自定义状态类
├── graph/                         # 图定义
│   └── SimpleGraph.java          # 示例图
├── tools/                         # Agent 工具
│   └── CustomTools.java          # 自定义工具
└── controller/                    # REST 控制器
    └── AgentController.java      # API 接口
```

## 最佳实践

### 1. 状态设计
- 使用清晰的状态 Schema
- 选择合适的 Reducer (覆盖 vs 追加)
- 保持状态轻量,避免存储大量数据

```java
public class MyState extends AgentState {
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        "messages", Channels.appender(ArrayList::new),
        "result", Channels.base()
    );
}
```

### 2. 节点设计
- 单一职责:每个节点专注于一个任务
- 幂等性:相同输入产生相同输出
- 错误处理:返回有意义的状态更新

```java
public class MyNode implements NodeAction<MyState> {
    @Override
    public Map<String, Object> apply(MyState state) {
        // 处理逻辑
        return Map.of("result", processedData);
    }
}
```

### 3. 图构建
- 从简单开始,逐步添加复杂性
- 使用条件边实现分支逻辑
- 利用 Checkpoints 调试复杂流程

```java
var graph = new StateGraph<>(MyState.SCHEMA, MyState::new)
    .addNode("node1", node1Action)
    .addNode("node2", node2Action)
    .addEdge(START, "node1")
    .addConditionalEdges("node1", routingFunction)
    .addEdge("node2", END)
    .compile();
```

### 4. 异步和流式处理
- 使用异步节点处理 I/O 密集型操作
- 利用流式输出实时响应 LLM 结果

```java
var graph = compileGraph();
for (var state : graph.stream(initialState)) {
    // 处理每个步骤的状态更新
}
```

### 5. 工具集成
- 使用 Spring AI 的 @Tool 注解定义工具
- 通过 AgentExecutor 自动调用工具

```java
public class MyTools {
    @Tool(description = "工具描述")
    public String myTool(@ToolParam(description = "参数") String param) {
        return "result";
    }
}
```

### 6. 可视化调试
- 启用 LangGraph4j Studio
- 生成 PlantUML 或 Mermaid 图表
- 使用 Checkpoints 检查中间状态

## 快速开始

### 前置要求
- JDK 21+
- Maven 3.9+
- OpenAI API Key (或其他支持的模型)

### 运行项目

1. 设置环境变量:
```bash
export OPENAI_API_KEY=your-api-key-here
```

2. 启动应用:
```bash
./mvnw spring-boot:run
```

3. 访问端点:
- 健康检查: `http://localhost:8080/actuator/health`
- Studio UI: `http://localhost:8080/studio` (可视化调试)

## 扩展指南

### 创建简单的状态图

```java
// 1. 定义状态
class SimpleState extends AgentState {
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
        "messages", Channels.appender(ArrayList::new)
    );
}

// 2. 定义节点
class GreeterNode implements NodeAction<SimpleState> {
    public Map<String, Object> apply(SimpleState state) {
        return Map.of("messages", "Hello!");
    }
}

// 3. 构建图
var graph = new StateGraph<>(SimpleState.SCHEMA, SimpleState::new)
    .addNode("greeter", new GreeterNode())
    .addEdge(START, "greeter")
    .addEdge("greeter", END)
    .compile();

// 4. 运行图
var result = graph.invoke(Map.of());
```

### 构建 Agent Executor

```java
// 使用 LangGraph4j 的 AgentExecutor
var agent = AgentExecutor.builder()
    .chatModel(chatModel)
    .toolsFromObject(new MyTools())
    .build()
    .compile();

// 执行 Agent
for (var item : agent.stream(Map.of("messages", "查询用户信息"))) {
    System.out.println(item);
}
```

### 条件路由示例

```java
var graph = new StateGraph<>(MyState.SCHEMA, MyState::new)
    .addNode("router", routerNode)
    .addNode("agent_a", agentA)
    .addNode("agent_b", agentB)
    .addConditionalEdges("router", state -> {
        String route = state.value("route", String.class).orElse("agent_a");
        return route;
    }, Map.of("agent_a", "agent_a", "agent_b", "agent_b"))
    .addEdge("agent_a", END)
    .addEdge("agent_b", END)
    .compile();
```

## 配置说明

### application.yml 主要配置项

- `spring.ai.openai.api-key`: OpenAI API 密钥
- `spring.ai.openai.chat.options.model`: 使用的模型
- `langgraph4j.studio.enabled`: 启用 Studio 可视化
- `langgraph4j.studio.path`: Studio 访问路径

## 参考资料

- [LangGraph4j 官方文档](https://langgraph4j.github.io/langgraph4j/)
- [LangGraph4j GitHub](https://github.com/langgraph4j/langgraph4j)
- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [LangGraph 原始 Python 版本](https://github.com/langchain-ai/langgraph)
- [LangGraph4j Examples](https://github.com/langgraph4j/langgraph4j-examples)

## License

MIT License
