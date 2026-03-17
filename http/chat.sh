#!/usr/bin/env bash

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "=== AI 채팅 메시지 전송 ==="
curl -s -X POST "${BASE_URL}/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"message": "안녕하세요! 코드 리뷰를 도와주세요."}' | jq .

echo ""
echo "=== 빈 메시지 전송 (유효성 검증 오류 확인) ==="
curl -s -X POST "${BASE_URL}/api/chat" \
  -H "Content-Type: application/json" \
  -d '{"message": ""}' | jq .
