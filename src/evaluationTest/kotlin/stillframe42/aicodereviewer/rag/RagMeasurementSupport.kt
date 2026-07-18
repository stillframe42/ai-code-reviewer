package stillframe42.aicodereviewer.rag

import stillframe42.aicodereviewer.rag.domain.model.RagDocument
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator
import org.springframework.ai.tokenizer.TokenCountEstimator
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.API
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.ARCH
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.SECURITY
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.STYLE

// RAG 측정 테스트용 공통 인프라 — RagContextMeasurementIT(베이스라인)와
// RagContextCompressionComparisonIT(압축 전/후 비교)가 공유한다.
// 각 IT 클래스가 서로를 참조하지 않도록 데이터 클래스/상수/헬퍼를 top-level로 분리.

// 측정 대상 단일 쿼리 (카테고리, 파일 경로, 검색 텍스트)
internal data class SampleQuery(
    val id: String,
    val queryText: String,
    val filePath: String,
    val expectedCategory: ConventionCategory,
)

// 청크 단위 측정 결과
internal data class ChunkMeasurement(
    val rank: Int,
    val tokens: Int,
    val chars: Int,
    val sourceFile: String?,
    val text: String,
)

// 쿼리별 종합 측정 결과
internal data class QueryMeasurement(
    val query: SampleQuery,
    val chunks: List<ChunkMeasurement>,
    val totalTokens: Int,    // 청크 토큰 합계 (구분자 제외)
    val joinedTokens: Int,   // join("\n\n---\n\n") 후 실제 프롬프트 주입 형태 토큰 수
)

// 측정 대상 쿼리 — 카테고리당 2개씩, FileCategoryMapper와 정확히 일치하도록 선정
// 카테고리 매핑 우선순위: SECURITY > API > ARCH > STYLE
internal val SAMPLE_QUERIES: List<SampleQuery> = listOf(
    // 모든 fixture 파일은 src/test/resources/fixtures/rag/sample-queries/ 하위에 보관 (self-contained)
    // FileCategoryMapper는 파일명만 보고 카테고리를 결정하므로 fixture 경로로도 동일 동작
    // ARCH (헥사고날 아키텍처, 트랜잭션, N+1)
    SampleQuery("arch-1", "OrderService.kt",
        "src/test/resources/fixtures/rag/sample-queries/OrderService.kt", ARCH),
    SampleQuery("arch-2", "ReviewRequestRepository.kt",
        "src/test/resources/fixtures/rag/sample-queries/ReviewRequestRepository.kt", ARCH),

    // API (Controller, REST)
    SampleQuery("api-1", "OrderController.kt",
        "src/test/resources/fixtures/rag/sample-queries/OrderController.kt", API),
    SampleQuery("api-2", "NotificationController.kt",
        "src/test/resources/fixtures/rag/sample-queries/NotificationController.kt", API),

    // STYLE (Kotlin 컨벤션) — 키워드 미매칭 파일명
    SampleQuery("style-1", "DiffPreprocessor.kt",
        "src/test/resources/fixtures/rag/sample-queries/DiffPreprocessor.kt", STYLE),
    SampleQuery("style-2", "ReviewMode.kt",
        "src/test/resources/fixtures/rag/sample-queries/ReviewMode.kt", STYLE),

    // SECURITY (인증, JWT)
    SampleQuery("sec-1", "JwtAuthenticationFilter.kt",
        "src/test/resources/fixtures/rag/sample-queries/JwtAuthenticationFilter.kt", SECURITY),
    SampleQuery("sec-2", "SecurityConfig.kt",
        "src/test/resources/fixtures/rag/sample-queries/SecurityConfig.kt", SECURITY),
)

// OpenAI cl100k_base 토크나이저 사용 — 임베딩 단계에서 사용되는 OpenAI text-embedding-3 기준
// Anthropic 토크나이저는 JVM에서 직접 사용 가능한 라이브러리가 없어 cl100k_base로 근사한다.
// 압축 전/후 비교에서는 동일 토크나이저를 사용하므로 절대값보다 상대 변화율이 의미 있다.
private val tokenEstimator: TokenCountEstimator = JTokkitTokenCountEstimator()

// 텍스트의 토큰 수를 반환 (cl100k_base 기준)
internal fun countTokens(text: String): Int = tokenEstimator.estimate(text)

// 검색된 Document 리스트로부터 청크별 + 종합 측정값 계산.
// 빈 List 는 빈 측정 결과(totalTokens=0, joinedTokens=0) 반환 — 압축 후 모든 청크 제외 케이스 지원.
internal fun measureChunks(query: SampleQuery, docs: List<RagDocument>): QueryMeasurement {
    val chunks = docs.mapIndexed { idx, doc ->
        val text = doc.text ?: ""
        ChunkMeasurement(
            rank = idx + 1,
            tokens = countTokens(text),
            chars = text.length,
            sourceFile = doc.metadata["source"] as? String,
            text = text,
        )
    }
    val joined = docs.joinToString("\n\n---\n\n") { it.text ?: "" }
    return QueryMeasurement(
        query = query,
        chunks = chunks,
        totalTokens = chunks.sumOf { it.tokens },
        joinedTokens = if (docs.isEmpty()) 0 else countTokens(joined),
    )
}
