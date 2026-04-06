package stillframe42.aicodereviewer.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.review.domain.model.PrImportance

class AiModelSelectorTest {

    private val properties = AiReviewerProperties(
        defaultModel = "claude-haiku-4-5-20251001",
        criticalModel = "claude-sonnet-4-6",
    )
    private val selector = AiModelSelector(properties)

    @Test
    fun `CRITICAL 중요도는 criticalModel을 반환한다`() {
        assertThat(selector.selectModel(PrImportance.CRITICAL))
            .isEqualTo("claude-sonnet-4-6")
    }

    @Test
    fun `NORMAL 중요도는 defaultModel을 반환한다`() {
        assertThat(selector.selectModel(PrImportance.NORMAL))
            .isEqualTo("claude-haiku-4-5-20251001")
    }
}
