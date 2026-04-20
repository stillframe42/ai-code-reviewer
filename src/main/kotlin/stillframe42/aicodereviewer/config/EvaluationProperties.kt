package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties

// 평가(LLM-as-a-Judge) 관련 설정 — app.rag.evaluation.* 프로퍼티에 바인딩
@ConfigurationProperties(prefix = "app.rag.evaluation")
data class EvaluationProperties(
    // 평가용 LLM 모델명 (비용 절감을 위해 gpt-4o-mini 기본값)
    val model: String = "gpt-4o-mini",
)
