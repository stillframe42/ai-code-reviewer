package stillframe42.aicodereviewer.rag.application

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.config.RagProperties
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase
import stillframe42.aicodereviewer.rag.domain.port.out.ConventionVectorPort
import stillframe42.aicodereviewer.rag.domain.port.out.DocumentPreparerPort
import kotlin.math.pow
import kotlin.time.toKotlinDuration
import kotlin.time.times

@Service
class DefaultConventionIndexService(
    private val vectorPort: ConventionVectorPort,
    private val splitter: DocumentPreparerPort,
    private val ragProperties: RagProperties,
    private val applicationArguments: ApplicationArguments,
) : ConventionIndexUseCase, Logging {

    companion object {
        private const val BATCH_SIZE = 20
        private const val MAX_RETRIES = 3
    }

    // 앱 기동 완료 시점에 자동 인덱싱을 시도한다.
    // ragProperties.autoIndex=false이면 스킵 (테스트 환경).
    // --index-conventions CLI 옵션이 있으면 스킵 — ConventionIndexingRunner가 처리함.
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (!ragProperties.autoIndex) return
        if (applicationArguments.containsOption("index-conventions")) return
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

    private suspend fun doIndex() {
        logger.info("컨벤션 문서 인덱싱을 시작합니다.")
        val documents = splitter.prepare()
        val batches = documents.chunked(BATCH_SIZE)
        var processed = 0
        batches.forEach { batch ->
            retryWithBackoff { vectorPort.save(batch) }
            processed += batch.size
            logger.info("[INDEX] {}/{} chunks processed", processed, documents.size)
        }
        logger.info("컨벤션 문서 인덱싱 완료. 총 {}건 저장.", documents.size)
    }

    // 실패 시 Exponential Backoff 재시도 (최대 MAX_RETRIES회)
    // 대기 시간: retryBaseDelay * 2^attempt (기본: 2s → 4s → 8s)
    private suspend fun retryWithBackoff(block: () -> Unit) {
        val baseDelay = ragProperties.retryBaseDelay.toKotlinDuration()
        repeat(MAX_RETRIES + 1) { attempt ->
            try {
                block()
                return
            } catch (e: Exception) {
                if (attempt >= MAX_RETRIES) throw e
                val delayDuration = baseDelay * 2.0.pow(attempt.toDouble())
                logger.warn(
                    "[RETRY] 인덱싱 시도 {}/{} 실패. {} 후 재시도. 원인: {}",
                    attempt + 1, MAX_RETRIES, delayDuration, e.message,
                )
                delay(delayDuration)
            }
        }
    }
}
