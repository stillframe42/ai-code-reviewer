package stillframe42.aicodereviewer.agent.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.agent.application.AgentReviewService
import stillframe42.aicodereviewer.github.domain.model.PrFile
import stillframe42.aicodereviewer.github.domain.model.PrFileStatus
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import stillframe42.aicodereviewer.integration.support.WireMockStubs

class AgentSessionIdPropagationIT : AbstractIntegrationTest() {

    @Autowired private lateinit var agentReviewService: AgentReviewService
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `reviewRequestId 가 outgoing 페이로드의 session_id 로 흐른다`(): Unit = runBlocking {
        stubRemoteAgent()
        WireMockStubs.stubOpenAiEmbedding(wireMock)

        agentReviewService.review(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 11,
            prDiff = "diff --git a/SecurityFilter.kt b/SecurityFilter.kt",
            prFiles = listOf(securityPrFile()),
            reviewRequestId = 99L,
        )

        val body = wireMock
            .findAll(postRequestedFor(urlPathEqualTo("/agent/analyze")))
            .single()
            .bodyAsString
        val tree = objectMapper.readTree(body)
        assertThat(tree.get("session_id").asText()).isEqualTo("99")
    }

    @Test
    fun `reviewRequestId 가 null 이면 outgoing 페이로드의 session_id 도 null 이다`(): Unit = runBlocking {
        stubRemoteAgent()
        WireMockStubs.stubOpenAiEmbedding(wireMock)

        agentReviewService.review(
            repositoryFullName = "owner/repo",
            pullRequestNumber = 12,
            prDiff = "diff --git a/SecurityFilter.kt b/SecurityFilter.kt",
            prFiles = listOf(securityPrFile()),
            reviewRequestId = null,
        )

        val body = wireMock
            .findAll(postRequestedFor(urlPathEqualTo("/agent/analyze")))
            .single()
            .bodyAsString
        val tree = objectMapper.readTree(body)
        assertThat(tree.get("session_id").isNull).isTrue()
    }

    // "filter" 키워드 포함 → SecurityFileDetector 가 SECURITY 카테고리로 분류
    private fun securityPrFile(): PrFile = PrFile(
        filename = "src/main/kotlin/SecurityFilter.kt",
        status = PrFileStatus.MODIFIED,
        additions = 5,
        deletions = 2,
        changes = 7,
        patch = "@@ -1,2 +1,5 @@\n+// security filter change",
        previousFilename = null,
    )

    // POST /agent/analyze 즉시 DONE, GET /agent/analyze/{id} 도 DONE — 폴러가 1회만에 종료
    private fun stubRemoteAgent() {
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze"))
                .willReturn(okJson("""{"analysis_id":"sid-test","status":"DONE","issues":[]}""")),
        )
        wireMock.stubFor(
            get(urlPathMatching("/agent/analyze/.+"))
                .willReturn(okJson("""{"analysis_id":"sid-test","status":"DONE","issues":[]}""")),
        )
    }


}
