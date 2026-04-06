package stillframe42.aicodereviewer.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// AiReviewerProperties YAML 바인딩 통합 테스트 — application-ai.yml 값이 올바르게 주입되는지 검증
class AiReviewerPropertiesTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var properties: AiReviewerProperties

    @Test
    fun `application-ai yml 값이 올바르게 바인딩된다`() {
        assertThat(properties.defaultModel).isEqualTo("claude-haiku-4-5-20251001")
        assertThat(properties.criticalModel).isEqualTo("claude-sonnet-4-6")
    }
}
