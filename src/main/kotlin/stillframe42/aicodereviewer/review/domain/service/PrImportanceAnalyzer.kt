package stillframe42.aicodereviewer.review.domain.service

import org.springframework.stereotype.Component
import org.springframework.util.AntPathMatcher
import stillframe42.aicodereviewer.config.AiReviewerProperties
import stillframe42.aicodereviewer.review.domain.model.PrImportance

// PR 중요도 분석 도메인 서비스 — 변경 파일 경로가 criticalPatterns에 매칭되면 CRITICAL 반환
@Component
class PrImportanceAnalyzer(private val properties: AiReviewerProperties) {

    private val matcher = AntPathMatcher()

    // 파일 목록 중 하나라도 CRITICAL 패턴에 매칭되면 CRITICAL 반환
    fun analyze(fileNames: List<String>): PrImportance {
        if (fileNames.isEmpty()) return PrImportance.NORMAL
        return if (fileNames.any { isCritical(it) }) PrImportance.CRITICAL
        else PrImportance.NORMAL
    }

    private fun isCritical(fileName: String): Boolean =
        properties.criticalPatterns.any { pattern -> matcher.match(pattern, fileName) }
}
