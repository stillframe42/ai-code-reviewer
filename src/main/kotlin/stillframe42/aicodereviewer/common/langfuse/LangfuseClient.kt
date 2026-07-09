package stillframe42.aicodereviewer.common.langfuse

import java.util.Base64
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
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

    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl(properties.host)
            .defaultHeader("Authorization", authHeader)
            .build()
    }

    // Langfuse /api/public/ingestion 엔드포인트에 배치 이벤트를 전송
    // 실패 시 RuntimeException throw — 호출부에서 try-catch로 처리할 것
    fun ingest(batch: List<Map<String, Any>>) {
        val body = mapOf("batch" to batch)
        webClient.post()
            .uri("/api/public/ingestion")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()
            .onStatus({ it.isError }) { response ->
                response.bodyToMono(String::class.java)
                    .defaultIfEmpty("응답 바디 없음")
                    .flatMap { responseBody ->
                        Mono.error(RuntimeException("Langfuse ingestion 실패: ${response.statusCode()} — $responseBody"))
                    }
            }
            .bodyToMono<Map<String, Any>>()
            .defaultIfEmpty(emptyMap())
            // Langfuse 무응답 시 LLM 호출 스레드가 무기한 매달리지 않도록 전체 교환에 타임아웃
            .timeout(properties.ingestTimeout)
            // ObservationHandler의 동기 콜백에서 호출되므로 블로킹 방식 사용
            .block()
        logger.debug("[LANGFUSE] ingestion 배치 전송 완료: {}건", batch.size)
    }
}
