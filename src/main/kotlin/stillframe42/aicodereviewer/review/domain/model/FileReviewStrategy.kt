package stillframe42.aicodereviewer.review.domain.model

// 파일 확장자별 AI 리뷰 처리 전략
// when 분기를 exhaustive하게 강제하여 새 전략 추가 시 누락을 컴파일 타임에 방지한다
sealed class FileReviewStrategy {
    // 일반 코드 품질 리뷰 (.kt, .java 등)
    object FullReview : FileReviewStrategy()

    // 변경 의도·설정값 적절성·구조적 영향 위주 리뷰 (.yml, .sql, .json 등)
    // diff 청크에 [QUERY_REVIEW] 헤더 주석이 삽입되어 AI에게 리뷰 방식을 전달한다
    object QueryReview : FileReviewStrategy()

    // 리뷰 대상에서 제외 (이미지, 바이너리, 빌드 산출물 등)
    object Skip : FileReviewStrategy()
}
