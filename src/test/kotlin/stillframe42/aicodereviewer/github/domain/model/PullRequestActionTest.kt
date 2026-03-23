package stillframe42.aicodereviewer.github.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PullRequestActionTest {

    @Test
    fun `OPENED, SYNCHRONIZE, REOPENED 세 가지 값이 존재한다`() {
        val values = PullRequestAction.entries
        assertEquals(3, values.size)
        assert(PullRequestAction.OPENED in values)
        assert(PullRequestAction.SYNCHRONIZE in values)
        assert(PullRequestAction.REOPENED in values)
    }

    @Test
    fun `문자열로부터 enum 값을 파싱할 수 있다`() {
        assertEquals(PullRequestAction.OPENED, enumValueOf<PullRequestAction>("OPENED"))
        assertEquals(PullRequestAction.SYNCHRONIZE, enumValueOf<PullRequestAction>("SYNCHRONIZE"))
        assertEquals(PullRequestAction.REOPENED, enumValueOf<PullRequestAction>("REOPENED"))
    }
}
