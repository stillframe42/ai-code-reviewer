package stillframe42.aicodereviewer.review.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewRequestEntity

interface ReviewRequestRepository : JpaRepository<ReviewRequestEntity, Long> {

    // 특정 레포/PR의 최신 리뷰 요청 조회 (생성 시각 내림차순)
    fun findTopByRepoFullNameAndPrNumberOrderByCreatedAtDesc(
        repoFullName: String,
        prNumber: Int,
    ): ReviewRequestEntity?
}
