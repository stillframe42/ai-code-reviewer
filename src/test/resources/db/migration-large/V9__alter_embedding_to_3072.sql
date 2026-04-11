-- text-embedding-3-large (3072차원) 실험용 스키마 변경
-- V5~V8 Flyway 마이그레이션 후 실행됨 (EmbeddingLargeModelExperimentTest에서만 사용)
-- HNSW 인덱스는 embedding 컬럼 삭제 시 자동 삭제됨 (실험용이므로 재생성 생략)
ALTER TABLE vector_store DROP COLUMN IF EXISTS embedding;
ALTER TABLE vector_store ADD COLUMN embedding vector(3072);
