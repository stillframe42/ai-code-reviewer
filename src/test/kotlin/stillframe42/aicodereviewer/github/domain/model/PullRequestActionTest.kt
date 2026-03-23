package stillframe42.aicodereviewer.github.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `fromString은 소문자 action 문자열을 올바른 enum으로 변환한다`() {
        assertEquals(PullRequestAction.OPENED, PullRequestAction.fromString("opened"))
        assertEquals(PullRequestAction.SYNCHRONIZE, PullRequestAction.fromString("synchronize"))
        assertEquals(PullRequestAction.REOPENED, PullRequestAction.fromString("reopened"))
    }

    @Test
    fun `fromString은 대소문자를 구분하지 않는다`() {
        assertEquals(PullRequestAction.OPENED, PullRequestAction.fromString("OPENED"))
        assertEquals(PullRequestAction.OPENED, PullRequestAction.fromString("Opened"))
    }

    @Test
    fun `fromString은 지원하지 않는 action 문자열에 null을 반환한다`() {
        assertNull(PullRequestAction.fromString("closed"))
        assertNull(PullRequestAction.fromString("labeled"))
        assertNull(PullRequestAction.fromString(""))
    }
}
