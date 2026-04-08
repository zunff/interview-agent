请分析这段音频，评估以下方面：

1. 转录文本：将语音转换为文字
2. 语调情感：分析语音语调是否自然、自信
3. 表达流畅度：评估说话是否流畅

请以JSON格式返回结果：
{
    "transcribedText": "转录的文本内容",
    "voiceToneScore": 75,
    "toneAnalysis": "语调分析描述",
    "emotionAnalysis": "情感分析描述",
    "suggestions": ["建议1", "建议2"]
}

评分范围0-100。
