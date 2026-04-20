package stillframe42.aicodereviewer.evaluation.application

import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.common.Logging
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.evaluation.domain.model.EvaluationResult
import stillframe42.aicodereviewer.evaluation.domain.model.GoldenCase
import stillframe42.aicodereviewer.evaluation.domain.port.`in`.EvaluationUseCase
import stillframe42.aicodereviewer.evaluation.domain.port.out.RagEvaluationPort
import stillframe42.aicodereviewer.rag.application.HybridConventionSearchService
import stillframe42.aicodereviewer.rag.domain.service.FileCategoryMapper
import stillframe42.aicodereviewer.review.domain.port.`in`.ReviewUseCase
import java.time.Instant

@Service
class DefaultEvaluationService(
    private val ragEvaluationPort: RagEvaluationPort,
    private val hybridSearchService: HybridConventionSearchService,
    private val reviewUseCase: ReviewUseCase,
) : EvaluationUseCase, Logging {

    override suspend fun evaluateAll(cases: List<GoldenCase>): List<EvaluationResult> =
        cases.mapIndexed { index, case ->
            logger.info("평가 진행: [{}/{}] {}", index + 1, cases.size, case.id)
            evaluateCase(case)
        }

    private suspend fun evaluateCase(case: GoldenCase): EvaluationResult {
        return runCatching {
            // 1. patch 파일 로드
            val patchContent = javaClass.classLoader
                .getResourceAsStream("fixtures/evaluation/${case.patchFile}")
                ?.bufferedReader()?.readText()
                ?: throw IllegalStateException("patch 파일 없음: ${case.patchFile}")

            // 2. patch에서 파일명 추출 → 카테고리 결정
            val fileName = extractFileName(patchContent)
            val category = FileCategoryMapper.selectCategory(fileName)

            // 3. 컨벤션 검색 — 프로덕션(ConventionContextService)과 동일하게 파일명을 쿼리로 사용
            val retrievedDocs = hybridSearchService.search(fileName, topK = 5, category = category)

            // 4. 코드 리뷰 생성
            val codeReview = reviewUseCase.reviewCode(patchContent, AiProvider.ANTHROPIC)

            // 5. 리뷰 텍스트 변환
            val generatedReviewText = codeReview.issues.joinToString("\n") { issue ->
                "[${issue.severity}] ${issue.description}"
            }

            // 6. 4가지 지표 평가
            val scores = listOf(
                ragEvaluationPort.evaluateFaithfulness(retrievedDocs, generatedReviewText),
                ragEvaluationPort.evaluateContextPrecision(fileName, retrievedDocs, case.relevantConvention),
                ragEvaluationPort.evaluateContextRecall(case.expectedIssues, retrievedDocs),
                ragEvaluationPort.evaluateAnswerRelevancy(patchContent, case.expectedIssues, generatedReviewText),
            )

            EvaluationResult(caseId = case.id, scores = scores, executedAt = Instant.now())
        }.getOrElse { e ->
            logger.error("평가 실패: caseId={}, error={}", case.id, e.message, e)
            EvaluationResult(
                caseId = case.id,
                scores = emptyList(),
                executedAt = Instant.now(),
            )
        }
    }

    // diff --git a/.../Foo.kt b/.../Foo.kt 헤더에서 파일명 추출
    private fun extractFileName(patchContent: String): String {
        val line = patchContent.lines().firstOrNull { it.startsWith("diff --git") } ?: return "unknown"
        return line.substringAfter(" b/").trim()
    }
}
