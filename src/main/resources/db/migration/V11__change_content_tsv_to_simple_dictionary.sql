-- content_tsv tsvector 딕셔너리를 'english' → 'simple'로 변경
-- 'english' 딕셔너리는 영어 형태소 분석만 지원하여 한국어 + 영어 혼합 컨벤션 문서의
-- 키워드 검색(FTS)이 한국어 질문에서 전혀 동작하지 않는 문제가 있었다.
-- 'simple' 딕셔너리는 형태소 분석 없이 소문자화만 수행하여
-- 한국어·영어 혼합 텍스트 모두에서 영어 기술 용어 기반 FTS가 가능하다.

-- GENERATED ALWAYS AS 컬럼은 딕셔너리만 변경하는 ALTER가 불가하므로 재생성
-- (컬럼 DROP 시 ix_vector_store_tsv GIN 인덱스도 자동 제거된다)
ALTER TABLE vector_store DROP COLUMN content_tsv;

ALTER TABLE vector_store
  ADD COLUMN content_tsv tsvector
  GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;

CREATE INDEX ix_vector_store_tsv ON vector_store USING GIN(content_tsv);
