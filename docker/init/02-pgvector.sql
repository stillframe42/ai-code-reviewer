-- docker/init/02-pgvector.sql
-- pgvector 확장 활성화 (테이블 생성은 Flyway V5에서 관리)
CREATE EXTENSION IF NOT EXISTS vector;
