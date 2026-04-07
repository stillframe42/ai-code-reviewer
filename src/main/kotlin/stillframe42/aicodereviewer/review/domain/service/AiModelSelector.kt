package stillframe42.aicodereviewer.review.domain.service

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.config.AiReviewerProperties
import stillframe42.aicodereviewer.review.domain.model.PrImportance

// PR 중요도를 모델명으로 변환하는 도메인 서비스 — PrImportanceAnalyzer와 함께 모델 선택 흐름을 구성한다
@Component
class AiModelSelector(private val properties: AiReviewerProperties) {

    fun selectModel(importance: PrImportance): String = when (importance) {
        PrImportance.CRITICAL -> properties.criticalModel
        PrImportance.NORMAL   -> properties.defaultModel
    }
}
