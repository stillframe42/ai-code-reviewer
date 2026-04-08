-- IVFFlat 인덱스 제거 후 HNSW로 교체
-- HNSW: 소규모 데이터셋(수백 건)에서 IVFFlat 대비 recall 우수, Spring AI 기본값
DROP INDEX IF EXISTS idx_code_conventions_embedding;

CREATE INDEX idx_code_conventions_embedding
    ON code_conventions
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
