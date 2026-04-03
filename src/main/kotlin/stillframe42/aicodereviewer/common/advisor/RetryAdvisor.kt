package stillframe42.aicodereviewer.common.advisor

import kotlin.time.Duration.Companion.seconds
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.CallAdvisor
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain
import org.springframework.core.Ordered
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import stillframe42.aicodereviewer.common.Logging

// API 오류 발생 시 Exponential Backoff 전략으로 재시도하는 어드바이저
// StreamAdvisor는 구현하지 않는다 — 스트리밍 도중 실패한 청크는 되돌릴 수 없어 재시도가 무의미하다
class RetryAdvisor(
    private val maxAttempts: Int = 2,
    private val order: Int = Ordered.HIGHEST_PRECEDENCE + 1,
) : CallAdvisor, Logging {

    init {
        require(maxAttempts >= 1) { "maxAttempts는 1 이상이어야 합니다: $maxAttempts" }
    }

    override fun getName(): String = "RetryAdvisor"

    // LoggingAdvisor(HIGHEST_PRECEDENCE)보다 안쪽에 위치 — 재시도 전체 소요시간이 로그에 반영됨
    override fun getOrder(): Int = order

    override fun adviseCall(request: ChatClientRequest, chain: CallAdvisorChain): ChatClientResponse {
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return chain.nextCall(request)
            } catch (e: Exception) {
                // 재시도 불필요한 예외이거나 마지막 시도라면 즉시 throw
                if (!isRetriable(e) || attempt == maxAttempts) throw e
                lastException = e
                logger.warn("[RETRY] attempt={}/{} after {}", attempt, maxAttempts, e::class.simpleName)
                // Exponential Backoff: 1차 1초, 2차 2초
                // adviseCall은 Java 인터페이스 메서드라 suspend 불가 — Dispatchers.IO에서 실행되므로 Thread.sleep 허용
                Thread.sleep(attempt.seconds.inWholeMilliseconds)
            }
        }
        throw lastException!!  // 컴파일러를 위한 코드 — 실제로는 도달 불가
    }

    // Rate Limit(429) 또는 5xx 서버 오류만 재시도 대상으로 허용
    // 4xx 클라이언트 오류(잘못된 요청, 인증 오류 등)는 재시도해도 결과가 바뀌지 않으므로 즉시 throw
    private fun isRetriable(e: Throwable): Boolean =
        e is HttpServerErrorException ||
        (e is HttpClientErrorException && e.statusCode.value() == 429)
}
