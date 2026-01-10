-- PostgreSQL Vector Extension (pgvector) 자동 설치
-- Spring Boot 시작 시 자동으로 실행되어 vector 타입을 사용할 수 있게 합니다.
CREATE EXTENSION IF NOT EXISTS vector;

-- Create sections table based on Section entity
CREATE TABLE IF NOT EXISTS sections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    document_id UUID NOT NULL,
    sequence_order INT NOT NULL,
    nav_title VARCHAR(200),
    content TEXT NOT NULL,
    content_hash VARCHAR(16),
    embedding vector (3072),
    related_characters_json TEXT, -- JSON format string
    related_events_json TEXT, -- JSON format string
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT fk_sections_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT uq_sections_document_sequence UNIQUE (document_id, sequence_order)
);

-- Index for FK lookup
CREATE INDEX IF NOT EXISTS idx_sections_document_id ON sections (document_id);
-- Index for vector similarity search (Optional - HNSW or IVFFlat)
-- CREATE INDEX ON sections USING hnsw (embedding vector_cosine_ops);