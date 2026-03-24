package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties

// 리뷰 동작 관련 외부 설정 — application.yml의 app.review 섹션과 바인딩
@ConfigurationProperties(prefix = "app.review")
data class ReviewProperties(
    val diff: DiffProperties = DiffProperties(),
) {
    // diff 전처리 옵션 설정
    data class DiffProperties(
        // 추가 제외 glob 패턴 목록 — DiffFilterOptions.additionalExcludePatterns와 병합
        val additionalExcludePatterns: List<String> = emptyList(),
        // 최대 허용 토큰 수 — null이면 한도 없음
        val maxTokens: Int? = null,
    )
}
