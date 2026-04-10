package stillframe42.aicodereviewer.rag.application

import kotlinx.coroutines.runBlocking
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.config.RagProperties
import stillframe42.aicodereviewer.rag.adapter.out.ai.MarkdownHeaderSplitter
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort

@Service
class DefaultConventionIndexService(
    private val vectorPort: ConventionVectorPort,
    private val splitter: MarkdownHeaderSplitter,
    private val ragProperties: RagProperties,
) : ConventionIndexUseCase, Logging {

    // 앱 기동 완료 시점에 자동 인덱싱을 시도한다.
    // ragProperties.autoIndex=false이면 스킵 (테스트 환경).
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (!ragProperties.autoIndex) return
        runBlocking { index() }
    }

    // 테이블이 비어 있을 때만 인덱싱 실행.
    override suspend fun index() {
        if (!vectorPort.isEmpty()) {
            logger.info("컨벤션 인덱스가 이미 존재합니다. 스킵합니다.")
            return
        }
        doIndex()
    }

    // 기존 데이터 전체 삭제 후 재인덱싱.
    override suspend fun reindex() {
        logger.info("컨벤션 문서 재인덱싱을 시작합니다.")
        vectorPort.deleteAll()
        doIndex()
    }

    private fun doIndex() {
        logger.info("컨벤션 문서 인덱싱을 시작합니다.")
        val documents = splitter.prepare()
        vectorPort.save(documents)
        logger.info("컨벤션 문서 인덱싱 완료. 총 {}건 저장.", documents.size)
    }
}
