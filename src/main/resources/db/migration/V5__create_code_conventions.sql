-- 코드 컨벤션 임베딩 테이블 — pgvector 기반 유사도 검색용
-- Testcontainers 환경에서는 init SQL이 실행되지 않으므로 여기서도 확장을 활성화한다
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE code_conventions (
    id         BIGSERIAL PRIMARY KEY,
    content    TEXT        NOT NULL,
    metadata   JSONB,
    embedding  vector(1536),    -- OpenAI text-embedding-3-small 차원
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- IVFFlat 인덱스: 코사인 유사도 기반 ANN 검색
CREATE INDEX idx_code_conventions_embedding
    ON code_conventions
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
