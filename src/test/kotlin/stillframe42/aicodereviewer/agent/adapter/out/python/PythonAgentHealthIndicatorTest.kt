package stillframe42.aicodereviewer.agent.adapter.out.python

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.Fault
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.health.contributor.Status
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest

// PythonAgentHealthIndicator 의 Actuator contract 회귀 안전망.
// AbstractIntegrationTest 의 공유 WireMock 으로 agent.python.url 이 라우팅되어 있으므로
// 별도 컨텍스트 없이 indicator 빈을 그대로 주입받는다.
class PythonAgentHealthIndicatorTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var indicator: PythonAgentHealthIndicator

    @Test
    fun `agent _health 200 응답 시 status UP`() {
        wireMock.stubFor(
            get(urlPathEqualTo("/health"))
                .willReturn(aResponse().withStatus(200)),
        )

        val health = indicator.health().block()
        assertThat(health).isNotNull
        assertThat(health!!.status).isEqualTo(Status.UP)
    }

    @Test
    fun `agent _health 5xx 응답 시 status DOWN`() {
        wireMock.stubFor(
            get(urlPathEqualTo("/health"))
                .willReturn(aResponse().withStatus(503)),
        )

        val health = indicator.health().block()
        assertThat(health).isNotNull
        assertThat(health!!.status).isEqualTo(Status.DOWN)
    }

    @Test
    fun `agent _health connection reset 시 status DOWN`() {
        wireMock.stubFor(
            get(urlPathEqualTo("/health"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
        )

        val health = indicator.health().block()
        assertThat(health).isNotNull
        assertThat(health!!.status).isEqualTo(Status.DOWN)
    }
}
