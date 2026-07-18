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

    // Tool Calling 응답 — 1차 응답: getPRDescription 도구 요청
    val REVIEW_WITH_TOOL_CALL = """
        {
          "id": "msg_tool_01",
          "type": "message",
          "role": "assistant",
          "content": [
            {
              "type": "tool_use",
              "id": "toolu_01",
              "name": "getPRDescription",
              "input": {"repositoryFullName": "owner/repo", "prNumber": 42}
            }
          ],
          "model": "claude-haiku-4-5-20251001",
          "stop_reason": "tool_use",
          "stop_sequence": null,
          "usage": { "input_tokens": 100, "output_tokens": 20 }
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
