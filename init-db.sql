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

-- =====================================================
-- 面试知识库表 (RAG)
-- =====================================================

-- 删除旧表（如果存在）
DROP TABLE IF EXISTS interview_knowledge;

-- 创建新的知识库表
CREATE TABLE interview_knowledge (
    id BIGSERIAL PRIMARY KEY,
    question TEXT NOT NULL,
    answer TEXT,
    question_type VARCHAR(50) NOT NULL,
    company VARCHAR(100),
    job_position VARCHAR(100),
    category VARCHAR(100),
    difficulty VARCHAR(20),
    source VARCHAR(100),
    tags TEXT[],
    embedding vector(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建 HNSW 向量索引 (1024维，适配通义千问 embedding)
CREATE INDEX idx_knowledge_embedding ON interview_knowledge
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 创建元数据索引
CREATE INDEX idx_knowledge_type ON interview_knowledge(question_type);
CREATE INDEX idx_knowledge_company ON interview_knowledge(company);
CREATE INDEX idx_knowledge_job ON interview_knowledge(job_position);
CREATE INDEX idx_knowledge_category ON interview_knowledge(category);

-- 添加注释
COMMENT ON TABLE interview_session IS '面试会话表';
COMMENT ON TABLE answer_record IS '面试回答记录表';
COMMENT ON TABLE evaluation_record IS '面试评估记录表';
COMMENT ON TABLE interview_knowledge IS '面试知识库表';
COMMENT ON COLUMN interview_knowledge.id IS '主键ID';
COMMENT ON COLUMN interview_knowledge.question IS '问题内容';
COMMENT ON COLUMN interview_knowledge.answer IS '参考答案';
COMMENT ON COLUMN interview_knowledge.question_type IS '面试类型：技术面/业务面/HR面试';
COMMENT ON COLUMN interview_knowledge.company IS '关联公司';
COMMENT ON COLUMN interview_knowledge.job_position IS '关联岗位';
COMMENT ON COLUMN interview_knowledge.category IS '分类';
COMMENT ON COLUMN interview_knowledge.difficulty IS '难度';
COMMENT ON COLUMN interview_knowledge.source IS '来源';
COMMENT ON COLUMN interview_knowledge.tags IS '标签数组';
COMMENT ON COLUMN interview_knowledge.embedding IS '向量嵌入（1024维）';

-- =====================================================
-- Spring AI VectorStore 表 (由框架自动管理)
-- =====================================================

-- Spring AI 会自动创建 vector_store 表，此处仅作注释说明
-- 如果需要手动创建，可使用以下 SQL:
-- CREATE TABLE IF NOT EXISTS vector_store (
--     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
--     content TEXT,
--     metadata JSONB,
--     embedding vector(1024)
-- );
