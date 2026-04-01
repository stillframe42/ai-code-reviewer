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
