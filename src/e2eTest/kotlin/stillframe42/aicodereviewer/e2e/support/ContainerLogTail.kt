package stillframe42.aicodereviewer.e2e.support

import org.testcontainers.containers.output.OutputFrame
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Consumer

// Remote 에이전트 컨테이너 stdout 을 in-memory 로 수집 — 테스트 코드에서 predicate 기반 검색 가능.
// Slf4jLogConsumer 만으로는 콘솔 출력만 되고 테스트에서 query 가 불가.
class ContainerLogTail {

    private val lines = ConcurrentLinkedQueue<String>()

    val consumer: Consumer<OutputFrame> = Consumer { frame ->
        lines += frame.utf8String
    }

    fun snapshot(): List<String> = lines.toList()

    // 주어진 predicate 가 한 줄이라도 만족할 때까지 polling. timeout 초과 시 false 반환.
    fun await(timeout: Duration, predicate: (String) -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (lines.any(predicate)) return true
            Thread.sleep(100)
        }
        return false
    }
}
