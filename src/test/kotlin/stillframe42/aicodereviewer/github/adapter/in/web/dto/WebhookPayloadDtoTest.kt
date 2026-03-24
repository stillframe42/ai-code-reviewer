package stillframe42.aicodereviewer.github.adapter.`in`.web.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.github.domain.model.PullRequestAction

// WebhookPayloadDto JSON 파싱 및 toDomain() 변환 단위 테스트 (Spring 컨텍스트 없음)
class WebhookPayloadDtoTest {

    private val objectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
    }

    @Test
    fun `GitHub pull_request webhook JSON을 WebhookPayloadDto로 파싱한다`() {
        val json = """
            {
              "action": "opened",
              "installation": { "id": 12345678 },
              "repository": { "full_name": "owner/repo" },
              "pull_request": {
                "number": 42,
                "head": { "sha": "abc123def456" },
                "title": "feat: 새로운 기능",
                "user": { "login": "octocat" }
              }
            }
        """.trimIndent()

        val dto = objectMapper.readValue<WebhookPayloadDto>(json)

        assertEquals("opened", dto.action)
        assertEquals(12345678L, dto.installation.id)
        assertEquals("owner/repo", dto.repository.fullName)
        assertEquals(42, dto.pullRequest.number)
        assertEquals("abc123def456", dto.pullRequest.head.sha)
        assertEquals("feat: 새로운 기능", dto.pullRequest.title)
        assertEquals("octocat", dto.pullRequest.user.login)
    }

    @Test
    fun `toDomain은 opened action을 OPENED PullRequestEvent로 변환한다`() {
        val dto = buildDto(action = "opened")

        val event = dto.toDomain()

        assertNotNull(event)
        assertEquals(PullRequestAction.OPENED, event!!.action)
        assertEquals(12345678L, event.installationId)
        assertEquals("owner/repo", event.repositoryFullName)
        assertEquals(42, event.pullRequestNumber)
        assertEquals("abc123", event.headSha)
        assertEquals("feat: 새로운 기능", event.title)
        assertEquals("octocat", event.author)
    }

    @Test
    fun `toDomain은 synchronize action을 SYNCHRONIZE PullRequestEvent로 변환한다`() {
        val event = buildDto(action = "synchronize").toDomain()

        assertNotNull(event)
        assertEquals(PullRequestAction.SYNCHRONIZE, event!!.action)
    }

    @Test
    fun `toDomain은 reopened action을 REOPENED PullRequestEvent로 변환한다`() {
        val event = buildDto(action = "reopened").toDomain()

        assertNotNull(event)
        assertEquals(PullRequestAction.REOPENED, event!!.action)
    }

    @Test
    fun `toDomain은 지원하지 않는 action이면 null을 반환한다`() {
        assertNull(buildDto(action = "closed").toDomain())
        assertNull(buildDto(action = "labeled").toDomain())
    }

    private fun buildDto(action: String) = WebhookPayloadDto(
        action = action,
        installation = WebhookPayloadDto.InstallationDto(id = 12345678L),
        repository = WebhookPayloadDto.RepositoryDto(fullName = "owner/repo"),
        pullRequest = WebhookPayloadDto.PullRequestDto(
            number = 42,
            head = WebhookPayloadDto.PullRequestDto.HeadDto(sha = "abc123"),
            title = "feat: 새로운 기능",
            user = WebhookPayloadDto.PullRequestDto.UserDto(login = "octocat"),
        ),
    )
}
