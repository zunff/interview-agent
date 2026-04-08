-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 面试会话表
CREATE TABLE IF NOT EXISTS interview_session (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(32) UNIQUE NOT NULL,
    resume TEXT,
    job_info TEXT,
    interview_type VARCHAR(20),
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

-- 向量存储表 (用于 RAG 知识库)
CREATE TABLE IF NOT EXISTS interview_knowledge (
    id SERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    embedding vector(1536),
    metadata JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 向量索引 (使用 ivfflat 索引)
CREATE INDEX IF NOT EXISTS interview_knowledge_embedding_idx
ON interview_knowledge USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- 添加注释
COMMENT ON TABLE interview_session IS '面试会话表';
COMMENT ON TABLE answer_record IS '面试回答记录表';
COMMENT ON TABLE evaluation_record IS '面试评估记录表';
COMMENT ON TABLE interview_knowledge IS '面试知识库向量表';