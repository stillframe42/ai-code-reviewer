package stillframe42.aicodereviewer.chat.application

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.chat.domain.port.`in`.ChatUseCase
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs

// DefaultChatService 통합 테스트 — WireMock으로 AI API를 모킹합니다.
class DefaultChatServiceTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var chatUseCase: ChatUseCase

    @BeforeEach
    fun setUpStubs() {
        // RAG 컨벤션 컨텍스트 조회 시 OpenAI 임베딩 API 호출 — 스텁 필수
        WireMockStubs.stubOpenAiEmbedding(wireMock)
        // WireMock은 LIFO 순서로 매칭 — 나중에 등록된 스텁이 먼저 검사된다
        // 스트리밍 스텁을 나중에 등록해야 stream=true 요청에 우선 매칭된다
        WireMockStubs.stubAnthropicChat(wireMock)
        WireMockStubs.stubAnthropicChatStream(wireMock)
    }

    @Test
    fun `AI에게 메시지를 보내고 응답을 받는다`() = runBlocking {
        val answer = chatUseCase.chat("안녕하세요. 한 문장으로 자기소개 해주세요.", AiProvider.ANTHROPIC)

        assertThat(answer).isNotBlank()
        Unit
    }

    @Test
    fun `시스템 프롬프트가 적용되어 코드 리뷰어로서 응답한다`() = runBlocking {
        val answer = chatUseCase.chat(
            "다음 코드의 문제점을 한 줄로 말해주세요: fun add(a: Int, b: Int) = a + b",
            AiProvider.ANTHROPIC
        )

        assertThat(answer).isNotBlank()
        Unit
    }

    @Test
    fun `스트리밍으로 AI 응답을 토큰 단위로 수신한다`() = runBlocking {
        val tokens = chatUseCase.streamChat("한 문장으로: 1+1은?", AiProvider.ANTHROPIC)
            .toList()

        assertThat(tokens).isNotEmpty
        assertThat(tokens.joinToString("")).isNotBlank()
        Unit
    }
}
