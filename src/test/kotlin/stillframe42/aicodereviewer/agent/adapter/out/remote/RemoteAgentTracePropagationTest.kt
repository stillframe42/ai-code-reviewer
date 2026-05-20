package stillframe42.aicodereviewer.agent.adapter.out.remote

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.agent.domain.model.AgentAnalysisCommand
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// W3C Trace Context: version-traceId(32hex)-spanId(16hex)-flags(2hex)
private const val W3C_TRACEPARENT = "^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$"

class RemoteAgentTracePropagationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var remoteAgentClient: RemoteAgentClient

    @Test
    fun `requestDeepAnalysis - 요청에 W3C traceparent 헤더가 주입된다`() = runTest {
        wireMock.stubFor(
            post(urlPathEqualTo("/agent/analyze"))
                .willReturn(okJson("""{"analysis_id":"x","status":"DONE","issues":[]}""")),
        )

        remoteAgentClient.requestDeepAnalysis(
            AgentAnalysisCommand(prNumber = 1, repo = "owner/repo", diff = "diff"),
        )

        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/agent/analyze"))
                .withHeader("traceparent", matching(W3C_TRACEPARENT)),
        )
    }

    @Test
    fun `getAnalysisResult - 폴링 요청에 W3C traceparent 헤더가 주입된다`() = runTest {
        wireMock.stubFor(
            get(urlPathEqualTo("/agent/analyze/id-1"))
                .willReturn(okJson("""{"analysis_id":"id-1","status":"IN_PROGRESS","issues":[]}""")),
        )

        remoteAgentClient.getAnalysisResult("id-1")

        wireMock.verify(
            getRequestedFor(urlPathEqualTo("/agent/analyze/id-1"))
                .withHeader("traceparent", matching(W3C_TRACEPARENT)),
        )
    }
}
