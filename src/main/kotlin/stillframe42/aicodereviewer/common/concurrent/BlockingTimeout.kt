package stillframe42.aicodereviewer.common.concurrent

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration
import stillframe42.aicodereviewer.common.langfuse.TraceContextPropagation

// 블로킹 작업을 새 가상 스레드에서 실행하고 벽시계 타임아웃을 적용한다 — 코루틴 withTimeout 의 동기 대체.
// 타임아웃 시 자식 스레드에 interrupt 를 걸지만 진행 중인 블로킹 호출(LLM 등)의 즉시 중단은 보장되지 않는다
// (기존 withTimeout 도 협조적 취소라 블로킹 .call() 을 중단하지 못했다 — 동작 동일).
object BlockingTimeout {
    fun <T> run(timeout: Duration, block: () -> T): T {
        // 함수 타입 값은 Callable 로 SAM 자동 변환되지 않으므로 람다 리터럴로 감싼다
        val captured = TraceContextPropagation.capture(block)
        val future = FutureTask(Callable { captured() })
        Thread.ofVirtual().start(future)
        return try {
            future.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw BlockingTimeoutException("작업이 제한 시간($timeout)을 초과했습니다", e)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }
}

class BlockingTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
