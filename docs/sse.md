# SSE 对接文档

简历分析系统采用两阶段架构，所有进度和对话内容均通过 SSE (Server-Sent Events) 推送前端。

## 架构概览

```
上传简历 → [阶段一·LangGraph4j 图] → 报告 + 雷达图（存DB） → [阶段二·Spring AI 流式 Agent] → 对话
              SSE 推送进度和雷达图                                SSE 推送增量文本
```

## SSE 事件格式

所有事件遵循标准 SSE 格式：

```
event:<事件类型>
data:<JSON 字符串>
```

### 事件类型一览

| event | 阶段 | 说明 |
|-------|------|------|
| `progress` | 一 | 节点执行进度 |
| `dimension_score` | 一 | 单维度评分完成 |
| `radar_chart` | 一 | 雷达图配置（ECharts JSON） |
| `message` | 二 | 对话增量文本 |
| `tool_status` | 二 | 工具调用状态 |
| `done` | 一/二 | 流结束标记 |
| `error` | 一/二 | 错误信息 |

---

## 阶段一：上传简历

### 请求

```
POST /api/chat/sessions/{sessionId}/upload
Content-Type: multipart/form-data
Accept: text/event-stream
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | File | 是 | 简历文件（PDF/DOCX/TXT） |
| `message` | String | 否 | 附带消息 |

### 事件流时序

```
progress(upload, running)
progress(upload, completed)
  ↓
progress(parse, running)
progress(parse, completed)
  ↓
progress(company, running)
progress(company, completed)
  ↓  ← 并行执行
progress(skill, running)      progress(exp, running)
progress(bg, running)         progress(potential, running)
dimension_score(skill, ...)   dimension_score(exp, ...)
dimension_score(bg, ...)      dimension_score(potential, ...)
progress(skill, completed)    progress(exp, completed)
progress(bg, completed)       progress(potential, completed)
  ↓
progress(report, running)
radar_chart({...})
progress(report, completed)
  ↓
done
```

### 前端接入示例

```javascript
async function uploadResume(sessionId, file) {
  const formData = new FormData();
  formData.append('file', file);

  const response = await fetch(`/api/chat/sessions/${sessionId}/upload`, {
    method: 'POST',
    body: formData,
  });

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop();

    let currentEvent = null;
    for (const line of lines) {
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim();
      } else if (line.startsWith('data:') && currentEvent) {
        const data = JSON.parse(line.slice(5).trim());
        handlePhase1Event(currentEvent, data);
      }
    }
  }
}

function handlePhase1Event(event, data) {
  switch (event) {
    case 'progress':
      updateProgress(data.node, data.status);
      break;
    case 'dimension_score':
      updateRadarChart(data.key, data.score);
      break;
    case 'radar_chart':
      renderFullRadarChart(JSON.parse(data.data));
      break;
    case 'done':
      // 跳转到详情页
      window.location.href = `/analysis/${sessionId}`;
      break;
    case 'error':
      showError(data.error);
      break;
  }
}
```

---

## 阶段二：流式对话

### 请求

```
POST /api/chat/sessions/{sessionId}/message
Content-Type: application/json
Accept: text/event-stream
```

```json
{
  "message": "这个候选人的技术能力怎么样？"
}
```

### 事件流时序

```
message(增量文本片段)     ← 多次，拼接到一起形成完整回复
message(增量文本片段)
message(增量文本片段)
  ...
done
```

#### 对话中调用工具

```
tool_status({state: "running", tools: [{name: "...", arguments: "..."}]})
message(增量文本片段)
message(增量文本片段)
  ...
done
```

### 前端接入示例

```javascript
async function sendMessage(sessionId, message) {
  const response = await fetch(`/api/chat/sessions/${sessionId}/message`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message }),
  });

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let fullText = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop();

    let currentEvent = null;
    for (const line of lines) {
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim();
      } else if (line.startsWith('data:') && currentEvent) {
        const raw = line.slice(5).trim();

        if (currentEvent === 'message') {
          fullText += raw;
          updateChatBubble(fullText);
        } else if (currentEvent === 'tool_status') {
          showToolRunning();
        } else if (currentEvent === 'done') {
          finalizeChatBubble(fullText);
        } else if (currentEvent === 'error') {
          const data = JSON.parse(raw);
          showError(data.error);
        }
      }
    }
  }
}
```

---

## 事件数据结构

### progress

```json
{ "node": "skill", "status": "running" }
{ "node": "skill", "status": "completed" }
{ "node": "skill", "status": "failed" }
```

`node` 取值：`upload` | `parse` | `company` | `skill` | `exp` | `bg` | `potential` | `report`

### dimension_score

```json
{ "key": "skill", "name": "技能", "score": 85, "comment": "核心技术栈扎实..." }
```

`key` 取值：`skill` | `exp` | `bg` | `potential`

前端收到此事件后可实时更新雷达图对应的维度数据。

### radar_chart

```json
{
  "data": "{\"dimensions\":[{\"name\":\"技能\",\"key\":\"skill\",\"score\":85,\"comment\":\"...\"}],\"overallScore\":78,\"overallLevel\":\"SENIOR\"}"
}
```

注意：`data` 字段是 JSON 字符串，需要二次解析。结构如下：

```json
{
  "dimensions": [
    { "name": "技能", "key": "skill", "score": 85, "comment": "..." },
    { "name": "经验", "key": "exp", "score": 78, "comment": "..." },
    { "name": "背景", "key": "bg", "score": 90, "comment": "..." },
    { "name": "潜力", "key": "potential", "score": 72, "comment": "..." }
  ],
  "overallScore": 81,
  "overallLevel": "SENIOR"
}
```

`overallLevel` 取值：`JUNIOR` | `MID` | `SENIOR` | `EXPERT`

可直接用于 ECharts 雷达图配置：

```javascript
function buildEChartsOption(radarData) {
  const indicators = radarData.dimensions.map(d => ({
    name: d.name,
    max: 100,
  }));
  const values = radarData.dimensions.map(d => d.score);

  return {
    radar: { indicator: indicators },
    series: [{
      type: 'radar',
      data: [{ value: values, name: '综合评分' }],
    }],
  };
}
```

### tool_status

**阶段二（流式对话）格式：**
```json
{ "state": "running", "tools": [{"name": "webSearch", "arguments": "{}"}] }
```

`tools` 数组包含本次调用的所有工具信息，前端可据此显示具体正在执行的工具名称。

前端应显示加载动画，等待后续 `message` 事件。

### done

```json
{ "message": "[DONE]" }
```

流结束标记，前端收到后关闭 EventSource / ReadableStream。

### error

```json
{ "error": "简历解析失败: 文件格式不正确" }
```

---

## 完整对接流程

```
1. POST /api/chat/sessions
   ← { sessionId: "xxx", status: "ACTIVE" }

2. POST /api/chat/sessions/{sessionId}/upload  (SSE)
   ← progress / dimension_score / radar_chart / done 事件流
   → 收到 done 后，前端跳转到详情页

3. GET /api/chat/sessions/{sessionId}/analysis
   ← { hasAnalysis: true, radarChartData: "...", reportMarkdown: "...", analysisId: "..." }

4. POST /api/chat/sessions/{sessionId}/message  (SSE)
   ← message / tool_status / done 事件流
   → 拼接 message 事件数据展示对话内容
```

## 非 SSE 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat/sessions` | 创建会话 |
| GET | `/api/chat/sessions/{sessionId}/analysis` | 获取分析结果（含报告和雷达图） |
| GET | `/api/chat/sessions/{sessionId}` | 获取会话信息（含历史消息） |
| GET | `/api/chat/sessions` | 会话列表（分页） |
| DELETE | `/api/chat/sessions/{sessionId}` | 结束会话 |

## 注意事项

- SSE 连接超时为 300 秒，前端应设置对应的超时时间
- `message` 事件是增量文本，前端需拼接后显示
- 并行维度节点（skill/exp/bg/potential）的完成顺序不固定，前端应按 `key` 字段匹配而非顺序
- 阶段二对话中 Agent 会自动获取阶段一的分析报告作为上下文，前端无需额外传递
