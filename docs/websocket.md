# WebSocket 通信文档

## 连接信息

**连接地址**: `ws://localhost:8080/ws/interview`

所有消息格式统一为：
```json
{
  "type": "消息类型",
  "payload": { /* 消息内容 */ },
  "timestamp": 1699999999999
}
```

## 交互流程

```
客户端                                        服务端
  |                                            |
  |-------- WebSocket 连接 -------------------->|
  |                                            |
  |-------- start_interview ------------------>|
  |         (resume, jobInfo, ...)             |
  |                                            |
  |<-------- session_created ------------------|
  |         (sessionId)                        |
  |                                            |
  |<-------- self_intro -----------------------|
  |         (自我介绍阶段信号)                    |
  |                                            |
  |-------- audio_chunk (持续) ---------------->|
  |                                            |
  |-------- self_intro_complete ------------------>|
  |                                            |
  |<-------- answer_received ------------------|
  |<-------- new_question (第一道技术题) --------|
  |         (问题内容 + TTS 音频)                |
  |                                            |
  |-------- video_frame (持续) ---------------->|
  |-------- audio_chunk (持续) ---------------->|
  |                                            |
  |-------- answer_complete ------------------>|
  |                                            |
  |<-------- answer_received ------------------|
  |<-------- evaluation_result ----------------|
  |<-------- new_question (下一题) -------------|
  |         (或 final_report，面试结束)           |
  |                                            |
  | ... 循环直到面试结束 ...                      |
  |                                            |
```

## 客户端 → 服务端

### start_interview - 启动面试

**用途**：创建面试会话并启动图执行

**请求参数**：
```json
{
  "type": "start_interview",
  "resume": "简历文本内容",
  "jobInfo": "Java初级后端",
  "maxQuestions": 10,
  "maxFollowUps": 2
}
```

**说明**：
- `resume`（必填）：候选人简历文本
- `jobInfo`（必填）：目标岗位信息
- `maxQuestions`（可选，默认 10）：最大问题数
- `maxFollowUps`（可选，默认 2）：每题最大追问数
- 发送后服务端会先返回 `session_created`，然后异步执行图生成第一道题
- 第一道题通过 `new_question` 推送，同时推送 TTS 语音

### video_frame - 发送视频帧

**用途**：发送视频帧（缓存，answer_complete 时批量分析）

**请求参数**：
```json
{
  "type": "video_frame",
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
  "audio": "base64编码的音频数据"
}
```

**说明**：
- `audio`: Base64 编码的音频数据（PCM 格式，16kHz，16bit，单声道）
- 音频块会被缓存拼接，在 `answer_complete` 时取出进行语音分析
- 前端应使用 AudioContext 将录制的音频解码为 PCM 格式发送

### self_intro_complete - 自我介绍完成信号

**用途**：触发自我介绍 STT 转写、候选人画像分析和第一道技术题生成

**请求参数**：
```json
{
  "type": "self_intro_complete"
}
```

**说明**：
- 发送此信号表示候选人已完成自我介绍
- 服务端会取出缓存的音频数据进行 STT 转写，然后恢复图执行
- 处理完成后会推送 `new_question`（第一道技术题）
- 此阶段不进行视觉分析，前端只需发送 `audio_chunk`

### answer_complete - 回答完成信号

**用途**：触发缓存数据分析和下一题生成

**请求参数**：
```json
{
  "type": "answer_complete"
}
```

**说明**：
- 发送此信号表示候选人已完成当前问题的回答
- 服务端会取出缓存的视频帧和音频数据，进行多模态分析
- 分析完成后会推送 `evaluation_result` 和 `new_question`（或 `final_report`）

## 服务端 → 客户端

### session_created - 会话创建成功

**响应格式**：
```json
{
  "type": "session_created",
  "payload": {
    "sessionId": "dce43fc34e0a4221"
  },
  "timestamp": 1699999999999
}
```

**说明**：
- 收到 `start_interview` 后立即返回，包含分配的 sessionId
- 随后服务端异步执行图，生成第一道题后推送 `new_question`

### self_intro - 自我介绍阶段信号

**响应格式**：
```json
{
  "type": "self_intro",
  "payload": {},
  "timestamp": 1699999999999
}
```

**说明**：
- 面试启动后推送，表示进入自我介绍阶段
- 前端收到后展示自我介绍引导 UI
- 自我介绍阶段只需发送 `audio_chunk`（语音），不需要 `video_frame`
- 自我介绍完成后发送 `answer_complete`，流程与正常问答相同
- 服务端完成 STT 转写和候选人画像分析后，推送第一道技术题

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
    "format": "opus"
  },
  "timestamp": 1699999999999
}
```

**说明**：
- 语音问题合成开始时推送（使用 Qwen-TTS-Realtime WebSocket 模式）
- `format`: 音频格式（Opus，24kHz）

### audio_question_chunk - 语音问题音频块

**传输方式**: WebSocket Binary Frame（非 JSON 文本消息）

**说明**：
- 服务端通过 **WebSocket BinaryMessage** 直接发送原始 Opus 音频字节流
- 每个二进制帧为一个音频分片，客户端应按序拼接
- 音频格式：Opus，采样率 24000Hz
- 客户端可使用 Opus 解码器或直接播放

> **注意**：此消息不再以 JSON 文本形式发送，而是直接作为二进制帧传输，以减少 Base64 编码开销和延迟。

### audio_question_end - 语音问题结束

**响应格式**：
```json
{
  "type": "audio_question_end",
  "payload": {
    "sessionId": "interview-xxx"
  },
  "timestamp": 1699999999999
}
```

**说明**：
- 语音问题合成完成时推送（所有二进制音频帧已发送完毕）

### audio_question_error - 语音问题错误

**响应格式**：
```json
{
  "type": "audio_question_error",
  "payload": {
    "message": "语音合成失败: xxx"
  },
  "timestamp": 1699999999999
}
```

**说明**：
- 语音问题合成过程中发生错误时推送（降级为纯文字模式）

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
