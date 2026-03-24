package stillframe42.aicodereviewer.github.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PullRequestEventTest {

    private val sampleEvent = PullRequestEvent(
        action = PullRequestAction.OPENED,
        installationId = 12345L,
        repositoryFullName = "myorg/myrepo",
        pullRequestNumber = 42,
        headSha = "abc123def456",
    )

    @Test
    fun `PullRequestEvent 프로퍼티가 올바르게 저장된다`() {
        assertEquals(PullRequestAction.OPENED, sampleEvent.action)
        assertEquals(12345L, sampleEvent.installationId)
        assertEquals("myorg/myrepo", sampleEvent.repositoryFullName)
        assertEquals(42, sampleEvent.pullRequestNumber)
        assertEquals("abc123def456", sampleEvent.headSha)
    }

    @Test
    fun `동일한 값을 가진 두 PullRequestEvent는 동등하다`() {
        val other = sampleEvent.copy()
        assertEquals(sampleEvent, other)
    }


}
