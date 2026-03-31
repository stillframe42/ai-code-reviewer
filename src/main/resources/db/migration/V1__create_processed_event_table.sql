-- 처리된 PR 이벤트 이력 테이블 (신규 설치용)
-- 기존 운영 DB는 baseline-version: 1 설정으로 이 파일을 건너뜀
CREATE TABLE processed_pull_request_event (
    id                     BIGSERIAL PRIMARY KEY,
    repository_full_name   VARCHAR(255) NOT NULL,
    pull_request_number    INTEGER NOT NULL,
    head_sha               VARCHAR(40) NOT NULL,
    review_id              BIGINT NOT NULL,
    processed_at           TIMESTAMP NOT NULL,
    CONSTRAINT ux_processed_event
        UNIQUE (repository_full_name, pull_request_number, head_sha)
);
