package stillframe42.aicodereviewer.chat.application

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import stillframe42.aicodereviewer.chat.domain.port.`in`.ChatUseCase
import stillframe42.aicodereviewer.core.AiProvider

// ChatService 통합 테스트 — 실제 AI API를 호출합니다.
// 실제 API 키가 설정된 환경에서만 실행됩니다.
@SpringBootTest
class ChatServiceTest {

    @Autowired
    private lateinit var chatUseCase: ChatUseCase

    @Test
    fun `실제 AI에게 메시지를 보내고 응답을 받는다`() = runBlocking {
        // 더미 키("test-dummy-key")인 경우 테스트 스킵
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
            ?: System.getProperty("anthropic.api-key")
        assumeTrue(
            apiKey != null && apiKey.isNotBlank() && apiKey != "test-dummy-key",
            "실제 ANTHROPIC_API_KEY가 설정된 환경에서만 실행됩니다"
        )

        val answer = chatUseCase.chat("안녕하세요. 한 문장으로 자기소개 해주세요.", AiProvider.ANTHROPIC)

        assertThat(answer).isNotBlank()
    }

    @Test
    fun `시스템 프롬프트가 적용되어 코드 리뷰어로서 응답한다`() = runBlocking {
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
            ?: System.getProperty("anthropic.api-key")
        assumeTrue(
            apiKey != null && apiKey.isNotBlank() && apiKey != "test-dummy-key",
            "실제 ANTHROPIC_API_KEY가 설정된 환경에서만 실행됩니다"
        )

        val answer = chatUseCase.chat(
            "다음 코드의 문제점을 한 줄로 말해주세요: fun add(a: Int, b: Int) = a + b",
            AiProvider.ANTHROPIC
        )

        assertThat(answer).isNotBlank()
    }

    @Test
    fun `스트리밍으로 AI 응답을 토큰 단위로 수신한다`() = runBlocking {
        val apiKey = System.getenv("ANTHROPIC_API_KEY")
            ?: System.getProperty("anthropic.api-key")
        assumeTrue(
            apiKey != null && apiKey.isNotBlank() && apiKey != "test-dummy-key",
            "실제 ANTHROPIC_API_KEY가 설정된 환경에서만 실행됩니다"
        )

        val tokens = chatUseCase.streamChat("한 문장으로: 1+1은?", AiProvider.ANTHROPIC)
            .toList()

        assertThat(tokens).isNotEmpty
        assertThat(tokens.joinToString("")).isNotBlank()
    }
}
