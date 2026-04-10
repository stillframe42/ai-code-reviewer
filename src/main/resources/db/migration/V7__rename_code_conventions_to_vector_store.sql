-- Spring AI pgvector 기본 테이블명(vector_store)에 맞추기 위해 rename
-- V6에서 생성한 HNSW 인덱스도 함께 rename
ALTER TABLE code_conventions RENAME TO vector_store;
ALTER INDEX idx_code_conventions_embedding RENAME TO vector_store_embedding_idx;
