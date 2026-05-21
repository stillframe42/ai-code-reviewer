package stillframe42.aicodereviewer.e2e.support

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant

// Tempo HTTP API 조회 헬퍼 — 분산 trace 종단 검증용.
class TempoQuery(private val tempoHttpPort: Int) {

    private val http: HttpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper()

    // 두 서비스의 span 을 한 trace 안에 모두 가진 trace 의 traceID 를 반환한다 (없으면 null).
    // TraceQL && 연산자: 한 trace 가 첫 조건에 맞는 span 과 둘째 조건에 맞는 span 을 모두 가질 때 매칭.
    fun findTraceLinkingBothServices(): String? {
        val traceQl =
            """{ resource.service.name = "ai-code-reviewer" } && """ +
                """{ resource.service.name = "ai-agent-service" }"""
        val now = Instant.now()
        val uri = URI.create(
            "http://localhost:$tempoHttpPort/api/search" +
                "?q=${URLEncoder.encode(traceQl, StandardCharsets.UTF_8)}" +
                "&start=${now.minusSeconds(600).epochSecond}" +
                "&end=${now.plusSeconds(60).epochSecond}" +
                "&limit=20",
        )
        val response = http.send(
            HttpRequest.newBuilder(uri).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        if (response.statusCode() != 200) return null
        return objectMapper.readTree(response.body())
            .path("traces")
            .firstOrNull()
            ?.path("traceID")
            ?.asText()
            ?.takeIf { it.isNotBlank() }
    }
}
