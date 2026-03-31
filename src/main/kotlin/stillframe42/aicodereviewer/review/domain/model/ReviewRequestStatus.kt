package stillframe42.aicodereviewer.review.domain.model

// 코드 리뷰 요청의 처리 상태
enum class ReviewRequestStatus {
    PENDING,     // 요청 접수 대기
    PROCESSING,  // AI 리뷰 진행 중
    DONE,        // 리뷰 완료
    FAILED,      // 리뷰 실패
}
