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
    fun `application-ai yml 기본값이 올바르게 바인딩된다`() {
        assertThat(ragProperties.chunkSize).isEqualTo(512)
        assertThat(ragProperties.minChunkSizeChars).isEqualTo(100)
        assertThat(ragProperties.minChunkLengthToEmbed).isEqualTo(50)
        assertThat(ragProperties.maxNumChunks).isEqualTo(10000)
        assertThat(ragProperties.keepSeparator).isTrue()
    }

    @Test
    fun `integration-test 프로파일의 auto-index 오버라이드가 적용된다`() {
        // application-integration-test.yml: app.rag.auto-index: false
        assertThat(ragProperties.autoIndex).isFalse()
    }
}
