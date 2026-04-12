-- 전문 검색(Full-text Search)을 위한 content_tsv 컬럼 추가
-- tsvector 타입의 저장 컬럼으로 to_tsvector 함수의 결과를 저장한다.
-- GENERATED ALWAYS AS ... STORED로 content 변경 시 자동 갱신된다.

ALTER TABLE vector_store
  ADD COLUMN content_tsv tsvector
  GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

-- 전문 검색 성능을 위한 GIN 인덱스 생성
CREATE INDEX ix_vector_store_tsv ON vector_store USING GIN(content_tsv);
