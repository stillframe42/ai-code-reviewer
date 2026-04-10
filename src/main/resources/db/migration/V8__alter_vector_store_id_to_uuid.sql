-- Spring AI PgVectorStore는 id 컬럼으로 UUID 타입을 사용한다.
-- V5에서 BIGSERIAL로 생성했으나 Spring AI 요구 사양에 맞춰 UUID로 교체한다.

-- 1. 기존 PK 제약 삭제 (시퀀스 참조 포함)
ALTER TABLE vector_store DROP CONSTRAINT IF EXISTS vector_store_pkey;

-- 2. 기존 id 컬럼 삭제 (bigint/bigserial + 시퀀스)
ALTER TABLE vector_store DROP COLUMN IF EXISTS id;
DROP SEQUENCE IF EXISTS vector_store_id_seq;

-- 3. uuid 타입 id 컬럼 추가 및 PK 설정
ALTER TABLE vector_store ADD COLUMN id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE vector_store ADD CONSTRAINT pk_vector_store PRIMARY KEY (id);
