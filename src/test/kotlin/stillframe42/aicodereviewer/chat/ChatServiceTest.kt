package stillframe42.aicodereviewer.chat

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

// ChatService 통합 테스트 — 실제 AI API를 호출합니다.
// 실제 API 키가 설정된 환경에서만 실행됩니다.
@SpringBootTest
class ChatServiceTest {

    @Autowired
    private lateinit var chatService: ChatService

    @Test
    fun `실제 AI에게 메시지를 보내고 응답을 받는다`() {
        // 더미 키("test-dummy-key")인 경우 테스트 스킵
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
            ?: System.getProperty("anthropic.api-key")
        assumeTrue(
            apiKey != null && apiKey.isNotBlank() && apiKey != "test-dummy-key",
            "실제 ANTHROPIC_API_KEY가 설정된 환경에서만 실행됩니다"
        )

        val answer = chatService.chat("안녕하세요. 한 문장으로 자기소개 해주세요.")

        assertThat(answer).isNotBlank()
    }
}
