package stillframe42.aicodereviewer.review.adapter.out.ai.tool

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.Logging

// 각 @Tool 메서드 호출을 래핑하여 소요 시간과 응답 크기를 로깅한다
// block()이 예외를 던지면 로그 없이 그대로 전파한다
// AOP로 전환 시 이 클래스의 log() 호출부만 제거하면 된다
@Component
class ToolCallLogger : Logging {

    fun <T> log(toolName: String, args: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        val elapsed = System.currentTimeMillis() - start
        val bytes = result.toString().toByteArray(Charsets.UTF_8).size
        logger.info("[TOOL] {}({}) → {}B in {}ms", toolName, args, bytes, elapsed)
        return result
    }
}
