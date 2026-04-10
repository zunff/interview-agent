-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 面试会话表
CREATE TABLE IF NOT EXISTS interview_session (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(32) UNIQUE NOT NULL,
    resume TEXT,
    job_info TEXT,
    max_questions INT DEFAULT 10,
    max_follow_ups INT DEFAULT 2,
    current_question_index INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'WAITING',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    report TEXT
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_session_id ON interview_session(session_id);
CREATE INDEX IF NOT EXISTS idx_status ON interview_session(status);

-- 回答记录表
CREATE TABLE IF NOT EXISTS answer_record (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(32) REFERENCES interview_session(session_id) ON DELETE CASCADE,
    question_index INT,
    answer_text TEXT,
    answer_audio TEXT,
    answer_frames JSONB,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_answer_session_id ON answer_record(session_id);
CREATE INDEX IF NOT EXISTS idx_answer_question_index ON answer_record(session_id, question_index);

-- 评估记录表
CREATE TABLE IF NOT EXISTS evaluation_record (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(32) REFERENCES interview_session(session_id) ON DELETE CASCADE,
    question_index INT,
    question TEXT,
    answer TEXT,
    accuracy INT,
    logic INT,
    fluency INT,
    confidence INT,
    emotion_score INT,
    body_language_score INT,
    voice_tone_score INT,
    overall_score INT,
    strengths JSONB,
    weaknesses JSONB,
    detailed_evaluation TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_eval_session_id ON evaluation_record(session_id);
CREATE INDEX IF NOT EXISTS idx_eval_question_index ON evaluation_record(session_id, question_index);

-- 添加注释
COMMENT ON TABLE interview_session IS '面试会话表';
COMMENT ON TABLE answer_record IS '面试回答记录表';
COMMENT ON TABLE evaluation_record IS '面试评估记录表';

-- =====================================================
-- 面试知识库表 (RAG) - 已废弃
-- =====================================================
-- 注意：项目已改用 Spring AI VectorStore（vector_store 表）
-- interview_knowledge 表不再使用，保留此处仅作历史参考
-- 元数据过滤在数据库层通过 JSONB 字段实现

-- =====================================================
-- Spring AI VectorStore 表 (由框架自动管理)
-- =====================================================

-- Spring AI 会自动创建 vector_store 表，此处仅作注释说明
-- 表结构包含：id(UUID), content(TEXT), metadata(JSONB), embedding(vector(1024))
-- 使用 text-embedding-v3 模型（1024维，与配置匹配）
