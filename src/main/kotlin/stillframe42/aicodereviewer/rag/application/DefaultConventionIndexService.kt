package stillframe42.aicodereviewer.rag.application

import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.rag.adapter.out.ai.DocumentPreprocessor
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort

@Service
class DefaultConventionIndexService(
    private val vectorPort: ConventionVectorPort,
    private val preprocessor: DocumentPreprocessor,
    // app.rag.auto-index=false로 설정하면 테스트 환경에서 자동 인덱싱 비활성화
    @param:Value("\${app.rag.auto-index:true}")
    private val autoIndex: Boolean,
) : ConventionIndexUseCase, Logging {

    // 앱 기동 완료 시점에 자동 인덱싱을 시도한다.
    // autoIndex=false이면 스킵 (테스트 환경).
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (!autoIndex) return
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
        val documents = preprocessor.prepare()
        vectorPort.save(documents)
        logger.info("컨벤션 문서 인덱싱 완료. 총 {}건 저장.", documents.size)
    }
}
