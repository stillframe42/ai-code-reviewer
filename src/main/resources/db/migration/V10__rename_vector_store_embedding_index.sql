-- HNSW 인덱스명을 프로젝트 네이밍 규칙(ix_ 접두사)에 맞게 변경
-- V7에서 vector_store_embedding_idx 로 생성됐으나 ix_ 접두사 규칙 미적용 상태였음
ALTER INDEX vector_store_embedding_idx RENAME TO ix_vector_store_embedding;
