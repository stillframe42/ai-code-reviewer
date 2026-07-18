package stillframe42.aicodereviewer.common.langfuse

import java.net.http.HttpClient
import java.util.Base64
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.RestClient
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.config.LangfuseProperties

// Langfuse REST API(/api/public/ingestion) 전송 클라이언트
// 모든 이벤트는 단일 배치 요청으로 전송된다
class LangfuseClient(
    private val properties: LangfuseProperties,
) : Logging {

    // Basic Auth: Base64(publicKey:secretKey)
    private val authHeader: String by lazy {
        val credentials = "${properties.publicKey}:${properties.secretKey}"
        "Basic ${Base64.getEncoder().encodeToString(credentials.toByteArray())}"
    }

    private val restClient: RestClient by lazy {
        val httpClient = HttpClient.newBuilder()
            // JDK HttpClient 의 HTTP/2 우선 협상이 h2 미지원 서버(WireMock 포함)에서 업그레이드 실패를 유발 — HTTP/1.1 고정
            .version(HttpClient.Version.HTTP_1_1)
            .build()
        val requestFactory = JdkClientHttpRequestFactory(httpClient)
        // Langfuse 무응답 시 LLM 호출 스레드가 무기한 매달리지 않도록 타임아웃
        requestFactory.setReadTimeout(properties.ingestTimeout)
        RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl(properties.host)
            .defaultHeader("Authorization", authHeader)
            .build()
    }

    // Langfuse /api/public/ingestion 엔드포인트에 배치 이벤트를 전송
    // 실패 시 RuntimeException throw — 호출부에서 try-catch로 처리할 것
    fun ingest(batch: List<Map<String, Any>>) {
        val body = mapOf("batch" to batch)
        restClient.post()
            .uri("/api/public/ingestion")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .onStatus({ it.isError }) { _, response ->
                val responseBody = response.body.readAllBytes().toString(Charsets.UTF_8).ifEmpty { "응답 바디 없음" }
                throw RuntimeException("Langfuse ingestion 실패: ${response.statusCode} — $responseBody")
            }
            .toBodilessEntity()
        logger.debug("[LANGFUSE] ingestion 배치 전송 완료: {}건", batch.size)
    }
}
