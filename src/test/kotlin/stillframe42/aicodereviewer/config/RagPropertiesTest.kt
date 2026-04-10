package stillframe42.aicodereviewer.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// RagProperties YAML 바인딩 통합 테스트 — 기본값 및 integration-test 프로파일 오버라이드 검증
class RagPropertiesTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var ragProperties: RagProperties

    @Test
    fun `auto-index 프로퍼티가 올바르게 바인딩된다`() {
        // integration-test 프로파일이므로 application-integration-test.yml의 설정 적용됨
        // 실제 application-ai.yml 기본값은 true이지만, 통합 테스트에서는 false로 설정됨
        assertThat(ragProperties.autoIndex).isFalse()
    }
}
