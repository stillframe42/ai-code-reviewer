package stillframe42.aicodereviewer.review.adapter.out.ai

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.github.support.GitHubTestCredentials
import stillframe42.aicodereviewer.review.domain.model.ReviewMode
import stillframe42.aicodereviewer.review.domain.port.out.AiReviewPort

// SpringAiReviewAdapter Tool Calling 통합 테스트
// 실제 AI API 키가 필요하므로 자격증명이 없는 환경에서는 자동 스킵된다
@SpringBootTest
class SpringAiReviewAdapterToolTest {

    @Autowired
    private lateinit var aiReviewPort: AiReviewPort

    @Test
    fun `installationId 없이 호출하면 Tool 없이 정상 리뷰를 반환한다`() = runBlocking {
        val anthropicKey = System.getenv("ANTHROPIC_API_KEY")
            ?: System.getProperty("anthropic.api-key")
        org.junit.jupiter.api.Assumptions.assumeTrue(
            anthropicKey != null && anthropicKey.isNotBlank() && anthropicKey != "test-dummy-key",
            "실제 ANTHROPIC_API_KEY가 필요합니다",
        )

        val result = aiReviewPort.reviewCode(
            code = "fun add(a: Int, b: Int) = a + b",
            provider = AiProvider.ANTHROPIC,
        )

        assertThat(result.overallScore).isBetween(0, 10)
        assertThat(result.summary).isNotBlank()
    }

    @Test
    fun `installationId 제공 시 Tool이 등록된 상태로 리뷰가 완료된다`() = runBlocking {
        val (installationId, repo, _) = GitHubTestCredentials.assumeFullCredentials()

        // import 구문이 있는 코드로 테스트 — LLM이 파일 조회를 시도할 수 있는 환경
        val result = aiReviewPort.reviewCode(
            code = """
                diff --git a/src/main/kotlin/com/example/Foo.kt b/src/main/kotlin/com/example/Foo.kt
                --- a/src/main/kotlin/com/example/Foo.kt
                +++ b/src/main/kotlin/com/example/Foo.kt
                @@ -1,3 +1,5 @@
                 package com.example
                +
                +import java.util.UUID
                +
                 fun generateId() = UUID.randomUUID().toString()
            """.trimIndent(),
            provider = AiProvider.ANTHROPIC,
            mode = ReviewMode.WithGitHubTools(installationId),
        )

        assertThat(result.overallScore).isBetween(0, 10)
        assertThat(result.summary).isNotBlank()
    }
}
