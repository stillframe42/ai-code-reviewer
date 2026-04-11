package stillframe42.aicodereviewer.rag.adapter.`in`.cli

import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase

// --index-conventions CLI 옵션 처리
// main()에서 WebApplicationType.NONE으로 설정했으므로 run() 완료 후 JVM이 자연 종료됨
@Component
class ConventionIndexingRunner(
    private val conventionIndexUseCase: ConventionIndexUseCase,
) : ApplicationRunner, Logging {

    override fun run(args: ApplicationArguments) {
        if (!args.containsOption("index-conventions")) return

        logger.info("CLI 인덱싱 모드로 실행됩니다.")
        runBlocking {
            if (args.containsOption("force")) {
                logger.info("--force 옵션 감지 — 전체 재인덱싱을 실행합니다.")
                conventionIndexUseCase.reindex()
            } else {
                conventionIndexUseCase.index()
            }
        }
        logger.info("CLI 인덱싱 완료.")
    }
}
