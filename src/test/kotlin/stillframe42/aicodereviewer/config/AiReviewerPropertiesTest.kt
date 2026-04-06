package stillframe42.aicodereviewer.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// AiReviewerProperties YAML 바인딩 통합 테스트 — integration-test 프로파일의 오버라이드 값이 주입되는지 검증
class AiReviewerPropertiesTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var properties: AiReviewerProperties

    @Test
    fun `integration-test 프로파일 설정이 올바르게 바인딩된다`() {
        assertThat(properties.defaultModel).isEqualTo("test-haiku-model")
        assertThat(properties.criticalModel).isEqualTo("test-sonnet-model")
    }

    @Test
    fun `criticalPatterns이 YAML에서 바인딩된다`() {
        assertThat(properties.criticalPatterns).contains("**/*Security*")
    }
}
