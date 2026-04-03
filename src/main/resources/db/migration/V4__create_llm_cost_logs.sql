-- LLM 호출 비용 로그 테이블 — 모델별 토큰 수와 예상 비용을 기록한다
CREATE TABLE llm_cost_logs (
    id                 BIGSERIAL PRIMARY KEY,
    review_request_id  BIGINT REFERENCES review_requests(id),
    model_name         VARCHAR(100) NOT NULL,
    prompt_tokens      INTEGER NOT NULL,
    completion_tokens  INTEGER NOT NULL,
    estimated_cost_usd NUMERIC(10, 6) NOT NULL,
    called_at          TIMESTAMP NOT NULL
);
CREATE INDEX ix_llm_cost_logs_request ON llm_cost_logs (review_request_id);
