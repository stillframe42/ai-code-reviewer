-- review_results에 tool_call_count 컬럼 추가
ALTER TABLE review_results ADD COLUMN tool_call_count INTEGER NOT NULL DEFAULT 0;

-- 이슈 카테고리 정규화 테이블 — 이슈 1건당 1행 저장 (통계 집계용)
CREATE TABLE review_issue_categories (
    id               BIGSERIAL PRIMARY KEY,
    review_result_id BIGINT NOT NULL REFERENCES review_results(id),
    category         VARCHAR(20) NOT NULL
);
CREATE INDEX ix_review_issue_categories_result ON review_issue_categories (review_result_id);
