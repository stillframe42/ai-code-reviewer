-- Spring AI pgvector 기본 테이블명(vector_store)에 맞추기 위해 rename
-- V6에서 생성한 HNSW 인덱스, PK 제약, 시퀀스도 함께 rename

-- 1. 테이블 rename
ALTER TABLE code_conventions RENAME TO vector_store;

-- 2. HNSW 인덱스 rename
ALTER INDEX idx_code_conventions_embedding RENAME TO vector_store_embedding_idx;

-- 3. 자동 생성 PK 제약 rename
ALTER TABLE vector_store RENAME CONSTRAINT code_conventions_pkey TO vector_store_pkey;

-- 4. 자동 생성 시퀀스 rename (id 컬럼 기본값이 참조)
ALTER SEQUENCE code_conventions_id_seq RENAME TO vector_store_id_seq;
