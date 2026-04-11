# WebSocket 通信文档

## 连接信息

**连接地址**: `ws://localhost:8080/ws/interview/{sessionId}`

所有消息格式统一为：
```json
{
  "type": "消息类型",
  "payload": { /* 消息内容 */ },
  "timestamp": 1699999999999
}
```

## 客户端 → 服务端

### video_frame - 发送视频帧

**用途**：发送视频帧（缓存，answer_complete 时批量分析）

**请求参数**：
```json
{
  "type": "video_frame",
  "sessionId": "interview-xxx",
  "frame": "base64编码的图片数据"
}
```

**说明**：
- `frame`: Base64 编码的 JPEG/PNG 图片，建议每 2-3 秒发送一帧关键帧
- 视频帧会被缓存，在 `answer_complete` 时批量取出进行视觉分析

### audio_chunk - 发送音频块

**用途**：发送音频块（缓存，answer_complete 时批量分析）

**请求参数**：
```json
{
  "type": "audio_chunk",
  "sessionId": "interview-xxx",
  "audio": "base64编码的音频数据"
}
```

**说明**：
- `audio`: Base64 编码的音频数据（PCM/WAV 格式）
- 音频块会被缓存拼接，在 `answer_complete` 时取出进行语音分析

### answer_complete - 回答完成信号

**用途**：触发缓存数据分析和下一题生成

**请求参数**：
```json
{
  "type": "answer_complete",
  "sessionId": "interview-xxx"
}
```

**说明**：
- 发送此信号表示候选人已完成当前问题的回答
- 服务端会取出缓存的视频帧和音频数据，进行多模态分析
- 分析完成后会推送 `evaluation_result` 和 `new_question`（或 `final_report`）

## 服务端 → 客户端

### answer_received - 回答已接收确认

**响应格式**：
```json
{
  "type": "answer_received",
  "payload": {
    "message": "回答已接收，正在评估中..."
  },
  "timestamp": 1699999999999
}
```

### new_question - 推送新面试问题

**响应格式**：
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

**字段说明**：
| 字段 | 类型 | 说明 |
|------|------|------|
| `content` | string | 问题内容 |
| `questionType` | string | 问题类型：技术基础/项目经验/技术难点/系统设计/业务理解/场景分析/沟通协作/职业素养/追问 |
| `questionIndex` | int | 问题序号（从 1 开始） |
| `isFollowUp` | boolean | 是否为追问 |

### evaluation_result - 答案评估结果

**响应格式**：
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

**字段说明**：
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

### final_report - 最终面试报告

**响应格式**：
```json
{
  "type": "final_report",
  "payload": {
    "report": "# 面试评估报告\n\n## 综合评价\n...\n\n## 技术能力\n..."
  },
  "timestamp": 1699999999999
}
```

**说明**：
- `report`: Markdown 格式的完整面试评估报告
- 面试结束时推送，包含综合评价、各维度得分、问题回顾等

### audio_question_start - 语音问题开始

**响应格式**：
```json
{
  "type": "audio_question_start",
  "payload": {
    "questionIndex": 1,
    "totalChunks": 10
  },
  "timestamp": 1699999999999
}
```

**说明**：
- 语音问题合成开始时推送
- `questionIndex`: 问题序号
- `totalChunks`: 预计的音频块数量

### audio_question_chunk - 语音问题音频块

**响应格式**：
```json
{
  "type": "audio_question_chunk",
  "payload": {
    "questionIndex": 1,
    "chunkIndex": 1,
    "totalChunks": 10,
    "audio": "base64编码的音频数据"
  },
  "timestamp": 1699999999999
}
```

**说明**：
- 语音问题的音频数据分片
- `chunkIndex`: 当前音频块索引（从1开始）
- `audio`: Base64编码的音频数据

### audio_question_end - 语音问题结束

**响应格式**：
```json
{
  "type": "audio_question_end",
  "payload": {
    "questionIndex": 1,
    "totalChunks": 10
  },
  "timestamp": 1699999999999
}
```

**说明**：
- 语音问题合成完成时推送

### audio_question_error - 语音问题错误

**响应格式**：
```json
{
  "type": "audio_question_error",
  "payload": {
    "questionIndex": 1,
    "message": "语音合成失败: xxx"
  },
  "timestamp": 1699999999999
}
```

**说明**：
- 语音问题合成过程中发生错误时推送

### error - 错误消息

**响应格式**：
```json
{
  "type": "error",
  "payload": {
    "message": "答案处理失败: xxx"
  },
  "timestamp": 1699999999999
}
```

