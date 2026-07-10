package stillframe42.aicodereviewer.rag.integration

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionContextUseCase

@TestPropertySource(properties = ["rag.enabled=false"])
class ConventionContextIdsDisabledIT : AbstractIntegrationTest() {

    @Autowired
    private lateinit var conventionContextService: ConventionContextUseCase

    @Test
    fun `buildContextIds — RAG OFF 시 빈 리스트 반환`() {
        val ids = runBlocking {
            conventionContextService.buildContextIds(query = "any")
        }
        assertThat(ids).isEmpty()
    }
}
