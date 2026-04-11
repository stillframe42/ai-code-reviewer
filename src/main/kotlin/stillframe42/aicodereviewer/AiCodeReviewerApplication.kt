package stillframe42.aicodereviewer

import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AiCodeReviewerApplication

fun main(args: Array<String>) {
    // CLI 인덱싱 모드: --app.rag.auto-index=false를 커맨드라인 인수로 추가하여 application-ai.yml보다 높은 우선순위로 오버라이드
    // setDefaultProperties()는 우선순위가 가장 낮아 YAML에 덮어써지므로 커맨드라인 인수 방식을 사용한다
    val effectiveArgs = if (args.contains("--index-conventions")) {
        args + "--app.rag.auto-index=false"
    } else {
        args
    }
    runApplication<AiCodeReviewerApplication>(*effectiveArgs) {
        // 웹서버 불필요 → WebApplicationType.NONE으로 설정
        // ApplicationRunner 완료 후 JVM이 자연 종료됨 (exitProcess() 불필요)
        if (args.contains("--index-conventions")) {
            setWebApplicationType(WebApplicationType.NONE)
        }
    }
}
