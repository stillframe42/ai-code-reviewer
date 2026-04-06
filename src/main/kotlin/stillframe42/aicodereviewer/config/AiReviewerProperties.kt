package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties

// AI 모델 선택 설정 — application-ai.yml의 app.ai.reviewer 섹션과 바인딩
@ConfigurationProperties(prefix = "app.ai.reviewer")
data class AiReviewerProperties(
    // 일반 PR에 사용할 기본 모델명
    val defaultModel: String = "claude-haiku-4-5-20251001",
    // 중요 PR(CRITICAL)에 사용할 모델명
    val criticalModel: String = "claude-sonnet-4-6",
)
