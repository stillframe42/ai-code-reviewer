package stillframe42.aicodereviewer.config

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.review.domain.model.PrImportance

// PR 중요도를 모델명으로 변환하는 컴포넌트 — Phase 2의 PrImportanceAnalyzer와 함께 사용된다
@Component
class AiModelSelector(private val properties: AiReviewerProperties) {

    fun selectModel(importance: PrImportance): String = when (importance) {
        PrImportance.CRITICAL -> properties.criticalModel
        PrImportance.NORMAL   -> properties.defaultModel
    }
}
