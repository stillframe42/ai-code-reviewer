package stillframe42.aicodereviewer.integration.support

// WireMock에서 Anthropic /v1/messages 응답으로 반환할 픽스처 상수
object AnthropicResponseFixtures {

    // 정상 리뷰 응답 — Spring AI AnthropicChatModel이 파싱하는 형식
    // content[0].text에 CodeReviewAiResponse JSON 포함
    val REVIEW_SUCCESS = """
        {
          "id": "msg_test_01",
          "type": "message",
          "role": "assistant",
          "content": [
            {
              "type": "text",
              "text": "{\"overall_score\": 7, \"summary\": \"[통합테스트] 코드가 전반적으로 양호합니다.\", \"issues\": [], \"positives\": [\"명확한 변수명\"]}"
            }
          ],
          "model": "claude-haiku-4-5-20251001",
          "stop_reason": "end_turn",
          "stop_sequence": null,
          "usage": { "input_tokens": 100, "output_tokens": 50 }
        }
    """.trimIndent()

    // 이슈 포함 리뷰 응답 — DefaultReviewServiceTest의 이슈 감지 테스트에서 사용
    val REVIEW_WITH_ISSUES = """
        {
          "id": "msg_test_03",
          "type": "message",
          "role": "assistant",
          "content": [
            {
              "type": "text",
              "text": "{\"overall_score\": 4, \"summary\": \"[통합테스트] 이슈가 발견된 코드입니다.\", \"issues\": [{\"id\": \"1\", \"category\": \"SECURITY\", \"severity\": \"MAJOR\", \"line\": 10, \"description\": \"잠재적 취약점이 있습니다\", \"suggestion\": \"개선이 필요합니다\"}], \"positives\": []}"
            }
          ],
          "model": "claude-haiku-4-5-20251001",
          "stop_reason": "end_turn",
          "stop_sequence": null,
          "usage": { "input_tokens": 100, "output_tokens": 80 }
        }
    """.trimIndent()

    // 정상 채팅 응답 — ChatController/DefaultChatService 테스트에서 사용
    val CHAT_SUCCESS = """
        {
          "id": "msg_test_02",
          "type": "message",
          "role": "assistant",
          "content": [
            {
              "type": "text",
              "text": "안녕하세요! 저는 AI 코드 리뷰어입니다."
            }
          ],
          "model": "claude-haiku-4-5-20251001",
          "stop_reason": "end_turn",
          "stop_sequence": null,
          "usage": { "input_tokens": 50, "output_tokens": 10 }
        }
    """.trimIndent()

    // 스트리밍 채팅 SSE 응답 — /api/chat/stream 테스트에서 사용
    // Anthropic 스트리밍 이벤트 형식 — 각 이벤트는 빈 줄로 구분된다
    val CHAT_STREAM_SUCCESS = "event: message_start\n" +
        "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_01\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[],\"model\":\"claude-haiku-4-5-20251001\",\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":10,\"output_tokens\":0}}}\n\n" +
        "event: content_block_start\n" +
        "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n" +
        "event: content_block_delta\n" +
        "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"안녕하세요\"}}\n\n" +
        "event: content_block_stop\n" +
        "data: {\"type\":\"content_block_stop\",\"index\":0}\n\n" +
        "event: message_delta\n" +
        "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":5}}\n\n" +
        "event: message_stop\n" +
        "data: {\"type\":\"message_stop\"}\n\n"

    // 단순 diff 픽스처 — getPrDiff WireMock stub 응답용
    val SIMPLE_DIFF = """
        diff --git a/src/Foo.kt b/src/Foo.kt
        index 1234567..abcdefg 100644
        --- a/src/Foo.kt
        +++ b/src/Foo.kt
        @@ -1,3 +1,5 @@
         class Foo {
        +    fun bar(): String {
        +        return "hello"
        +    }
         }
    """.trimIndent()
}
