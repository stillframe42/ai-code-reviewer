package stillframe42.aicodereviewer.review.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import stillframe42.aicodereviewer.review.adapter.out.persistence.entity.ReviewRequestEntity

interface ReviewRequestRepository : JpaRepository<ReviewRequestEntity, Long>
