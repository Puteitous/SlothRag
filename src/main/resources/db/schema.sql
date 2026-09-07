-- slothrag 数据库初始化脚本
-- 执行方式：psql -U postgres -h localhost -d slothrag -f schema.sql
-- 前置：CREATE DATABASE slothrag;（本脚本假定已在 slothrag 库内执行）

-- 向量扩展（BGE-M3 输出 1024 维；如需更换模型调整 vector(1024) 维度）
CREATE EXTENSION IF NOT EXISTS vector;

-- ============ 知识库 ============
CREATE TABLE IF NOT EXISTS kb (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    embedding_model VARCHAR(64)     DEFAULT 'BAAI/bge-m3',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ============ 文档 ============
CREATE TABLE IF NOT EXISTS doc (
    id          BIGSERIAL PRIMARY KEY,
    kb_id       BIGINT          NOT NULL REFERENCES kb (id) ON DELETE CASCADE,
    file_name   VARCHAR(255)    NOT NULL,
    file_path   VARCHAR(512),
    file_size   BIGINT,
    status      VARCHAR(32)     NOT NULL DEFAULT 'PENDING', -- PENDING/PARSING/CHUNKING/INDEXED/FAILED
    chunk_count INT             NOT NULL DEFAULT 0,
    error_msg   TEXT,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_doc_kb_id ON doc (kb_id);
CREATE INDEX IF NOT EXISTS idx_doc_status ON doc (status);

-- ============ 分块（含向量列） ============
CREATE TABLE IF NOT EXISTS chunk (
    id          BIGSERIAL PRIMARY KEY,
    doc_id      BIGINT          NOT NULL REFERENCES doc (id) ON DELETE CASCADE,
    kb_id       BIGINT          NOT NULL REFERENCES kb (id) ON DELETE CASCADE,
    seq         INT             NOT NULL, -- 块在文档内的序号
    content     TEXT            NOT NULL,
    heading_path VARCHAR(512),            -- 章节路径（如"第二章 > 2.1 > 架构"），用于来源追溯
    vector      vector(1024),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chunk_doc_id ON chunk (doc_id);
CREATE INDEX IF NOT EXISTS idx_chunk_kb_id ON chunk (kb_id);
-- HNSW 余弦相似度索引（检索走这里）
CREATE INDEX IF NOT EXISTS idx_chunk_vector ON chunk USING hnsw (vector vector_cosine_ops);
-- 关键词检索走 PG FTS（GIN 索引）
CREATE INDEX IF NOT EXISTS idx_chunk_content_fts ON chunk USING gin (to_tsvector('simple', content));

-- ============ 入库任务 ============
CREATE TABLE IF NOT EXISTS ingest_task (
    id             BIGSERIAL PRIMARY KEY,
    doc_id         BIGINT REFERENCES doc (id) ON DELETE SET NULL,
    kb_id          BIGINT          NOT NULL REFERENCES kb (id) ON DELETE CASCADE,
    status         VARCHAR(32)     NOT NULL DEFAULT 'QUEUED', -- QUEUED/RUNNING/SUCCESS/FAILED
    progress       INT             NOT NULL DEFAULT 0,        -- 0-100
    current_stage  VARCHAR(64),                               -- PARSE/CHUNK/EMBED/INDEX
    error_msg      TEXT,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ingest_task_kb_id ON ingest_task (kb_id);
CREATE INDEX IF NOT EXISTS idx_ingest_task_doc_id ON ingest_task (doc_id);
CREATE INDEX IF NOT EXISTS idx_ingest_task_status ON ingest_task (status);

-- ============ 会话 ============
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(64) NOT NULL UNIQUE, -- 关联 JSONL 会话文件
    title       VARCHAR(255),
    user_id     BIGINT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============ 用户 ============
CREATE TABLE IF NOT EXISTS "user" (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64) NOT NULL UNIQUE,
    password    VARCHAR(128) NOT NULL, -- bcrypt hash
    nickname    VARCHAR(64),
    role        VARCHAR(32) NOT NULL DEFAULT 'ADMIN',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
