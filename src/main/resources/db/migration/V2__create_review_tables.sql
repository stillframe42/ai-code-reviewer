-- 리뷰 요청 테이블 — 각 PR에 대한 리뷰 요청 이력
CREATE TABLE review_requests (
    id             BIGSERIAL PRIMARY KEY,
    repo_full_name VARCHAR(255) NOT NULL,
    pr_number      INTEGER NOT NULL,
    head_sha       VARCHAR(40) NOT NULL,
    status         VARCHAR(20) NOT NULL,  -- PENDING / PROCESSING / DONE / FAILED
    created_at     TIMESTAMP NOT NULL,
    completed_at   TIMESTAMP
);
CREATE INDEX ix_review_requests_repo_pr ON review_requests (repo_full_name, pr_number);

-- 리뷰 결과 테이블 — AI가 생성한 코드 리뷰 결과
CREATE TABLE review_results (
    id                BIGSERIAL PRIMARY KEY,
    review_request_id BIGINT NOT NULL REFERENCES review_requests(id),
    summary           TEXT,
    issues_json       TEXT,        -- JSON 직렬화된 이슈 목록
    model_name        VARCHAR(100),
    created_at        TIMESTAMP NOT NULL
);

-- Tool 호출 이력 테이블 — LLM이 실행한 Tool 호출 로그
CREATE TABLE tool_call_logs (
    id                BIGSERIAL PRIMARY KEY,
    review_request_id BIGINT NOT NULL REFERENCES review_requests(id),
    tool_name         VARCHAR(100) NOT NULL,
    arguments_json    TEXT,
    response_size     INTEGER,
    elapsed_ms        INTEGER,
    success           BOOLEAN NOT NULL,
    called_at         TIMESTAMP NOT NULL
);
CREATE INDEX ix_tool_call_logs_request ON tool_call_logs (review_request_id);
