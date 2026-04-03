package stillframe42.aicodereviewer.review.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

// ReviewContext 도메인 값 객체 단위 테스트
class ReviewContextTest {

    @Test
    fun `ReviewContext는 동일한 필드값이면 동등하다`() {
        val ctx1 = ReviewContext(reviewRequestId = 1L, prNumber = 42, repoFullName = "owner/repo")
        val ctx2 = ReviewContext(reviewRequestId = 1L, prNumber = 42, repoFullName = "owner/repo")

        assertThat(ctx1).isEqualTo(ctx2)
    }

    @Test
    fun `ReviewContext는 필드값이 다르면 동등하지 않다`() {
        val ctx1 = ReviewContext(reviewRequestId = 1L, prNumber = 42, repoFullName = "owner/repo")
        val ctx2 = ReviewContext(reviewRequestId = 2L, prNumber = 42, repoFullName = "owner/repo")

        assertThat(ctx1).isNotEqualTo(ctx2)
    }

    @Test
    fun `ReviewContext 필드값에 정상 접근된다`() {
        val ctx = ReviewContext(reviewRequestId = 99L, prNumber = 7, repoFullName = "alice/project")

        assertThat(ctx.reviewRequestId).isEqualTo(99L)
        assertThat(ctx.prNumber).isEqualTo(7)
        assertThat(ctx.repoFullName).isEqualTo("alice/project")
    }
}
