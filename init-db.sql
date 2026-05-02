-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 面试会话表
CREATE TABLE IF NOT EXISTS interview_session (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(32) UNIQUE NOT NULL,
    resume TEXT,
    job_info TEXT,
    max_technical_questions INT DEFAULT 6,
    max_business_questions INT DEFAULT 4,
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
    session_id VARCHAR(32),
    question_index INT,
    question TEXT,
    answer_text TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_answer_session_id ON answer_record(session_id);
CREATE INDEX IF NOT EXISTS idx_answer_question_index ON answer_record(session_id, question_index);

-- 评估记录表
CREATE TABLE IF NOT EXISTS evaluation_record (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(32),
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
    standard_answer TEXT,
    suggestions TEXT,
    is_follow_up BOOLEAN DEFAULT FALSE,
    question_type VARCHAR(128),
    difficulty VARCHAR(32),
    expected_keywords JSONB,
    modality_concern BOOLEAN DEFAULT FALSE,
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

-- =====================================================
-- 聊天会话表
-- =====================================================
CREATE TABLE IF NOT EXISTS chat_session (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(32) UNIQUE NOT NULL,
    resume_file_path VARCHAR(512),
    resume_text TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_session_id ON chat_session(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_session_status ON chat_session(status);

COMMENT ON TABLE chat_session IS '聊天会话表';

-- =====================================================
-- 简历分析表
-- =====================================================
CREATE TABLE IF NOT EXISTS resume_analysis (
    id SERIAL PRIMARY KEY,
    analysis_id VARCHAR(32) UNIQUE NOT NULL,
    session_id VARCHAR(32),
    file_name VARCHAR(255),
    file_type VARCHAR(20),
    resume_text TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    report_markdown TEXT,
    radar_chart_data TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_resume_analysis_id ON resume_analysis(analysis_id);
CREATE INDEX IF NOT EXISTS idx_resume_analysis_status ON resume_analysis(status);
CREATE INDEX IF NOT EXISTS idx_resume_analysis_session_id ON resume_analysis(session_id);

COMMENT ON TABLE resume_analysis IS '简历分析记录表';
COMMENT ON COLUMN resume_analysis.session_id IS '关联聊天会话ID（应用层维护）';
COMMENT ON COLUMN resume_analysis.radar_chart_data IS '雷达图数据JSON';

-- =====================================================
-- 公司研究缓存表
-- =====================================================
CREATE TABLE IF NOT EXISTS company (
    id SERIAL PRIMARY KEY,
    company_name VARCHAR(255) UNIQUE NOT NULL,
    raw_search_result TEXT,
    analyzed_result TEXT,
    expires_at TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    hit_count INT DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_company_name ON company(company_name);
CREATE INDEX IF NOT EXISTS idx_company_expires_at ON company(expires_at);

COMMENT ON TABLE company IS '公司研究缓存表';
COMMENT ON COLUMN company.company_name IS '公司名称（唯一键）';
COMMENT ON COLUMN company.raw_search_result IS '原始搜索结果';
COMMENT ON COLUMN company.analyzed_result IS 'LLM处理后的分析结果';
COMMENT ON COLUMN company.expires_at IS '缓存过期时间';
COMMENT ON COLUMN company.hit_count IS '缓存命中次数';

-- =====================================================
-- 图状态 Checkpoint 表（支持断连重连）
-- =====================================================
CREATE TABLE IF NOT EXISTS graph_checkpoint (
    id SERIAL PRIMARY KEY,
    thread_id VARCHAR(64) NOT NULL,
    checkpoint_id VARCHAR(128) NOT NULL,
    node_id VARCHAR(128),
    next_node_id VARCHAR(128),
    state JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_checkpoint_thread ON graph_checkpoint(thread_id);
CREATE INDEX IF NOT EXISTS idx_checkpoint_thread_id ON graph_checkpoint(thread_id, checkpoint_id);
COMMENT ON TABLE graph_checkpoint IS '图状态检查点表（支持面试断连重连）';