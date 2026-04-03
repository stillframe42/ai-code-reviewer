-- docker/init/01-langfuse-db.sql
-- postgres 컨테이너 최초 기동 시 자동 실행되어 langfuse 전용 DB를 생성한다.
CREATE DATABASE langfuse;
