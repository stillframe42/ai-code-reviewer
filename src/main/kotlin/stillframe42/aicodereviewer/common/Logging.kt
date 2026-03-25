package stillframe42.aicodereviewer.common

import org.slf4j.Logger
import org.slf4j.LoggerFactory

// logger 프로퍼티를 제공하는 믹스인 인터페이스
// 클래스에서 Logging 을 구현하면 별도 선언 없이 logger 를 바로 사용할 수 있다
// javaClass 는 런타임 실제 클래스를 반환하며, SLF4J 는 클래스명 기준으로 logger 를 캐싱한다
interface Logging {
    val logger: Logger get() = LoggerFactory.getLogger(javaClass)
}
