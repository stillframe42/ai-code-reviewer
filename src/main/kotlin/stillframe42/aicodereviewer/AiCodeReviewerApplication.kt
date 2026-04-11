package stillframe42.aicodereviewer

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AiCodeReviewerApplication

fun main(args: Array<String>) {
    runApplication<AiCodeReviewerApplication>(*args) {
        // CLI 인덱싱 모드: 웹서버 불필요 → WebApplicationType.NONE으로 설정
        // ApplicationRunner 완료 후 JVM이 자연 종료됨 (exitProcess() 불필요)
        if (args.contains("--index-conventions")) {
            setWebApplicationType(WebApplicationType.NONE)
        }
    }
}
