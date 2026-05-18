package stillframe42.aicodereviewer.e2e.support

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import java.time.Duration

// Spring Boot 의 임의 logger 에 Logback ListAppender 를 attach 하고
// 테스트에서 events 를 snapshot 으로 query 한다. ContainerLogTail (컨테이너 stdout) 의 자매.
class SpringBootLogTail {

    private val appender: ListAppender<ILoggingEvent> = ListAppender<ILoggingEvent>().also { it.start() }

    fun attachTo(loggerName: String) {
        val logger = LoggerFactory.getLogger(loggerName) as Logger
        if (appender !in logger.iteratorForAppenders().asSequence()) {
            logger.addAppender(appender)
        }
    }

    fun snapshot(): List<ILoggingEvent> = appender.list.toList()

    fun reset() {
        appender.list.clear()
    }

    // 주어진 predicate 가 한 줄이라도 만족할 때까지 polling. timeout 초과 시 false.
    fun await(timeout: Duration, predicate: (String) -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            if (snapshot().any { predicate(it.formattedMessage) }) return true
            Thread.sleep(100)
        }
        return false
    }
}
