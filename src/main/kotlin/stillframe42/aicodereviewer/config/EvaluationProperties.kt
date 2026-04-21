package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties

// 평가(LLM-as-a-Judge) 관련 설정 — app.rag.evaluation.* 프로퍼티에 바인딩
@ConfigurationProperties(prefix = "app.rag.evaluation")
data class EvaluationProperties(
    // 평가용 LLM 모델명 (비용 절감을 위해 gpt-4o-mini 기본값)
    // Sub-plan A 단계 2/3 측정 시 gpt-4o로 오버라이드 가능 (env var APP_RAG_EVALUATION_MODEL=gpt-4o)
    val model: String = "gpt-4o-mini",
    // Faithfulness 평가에 Few-shot 예시 프롬프트(evaluation-faithfulness-fewshot.st) 사용 여부.
    // Sub-plan A 단계 1/3 측정 시 true. production default false (sub-plan 종결 시 통과 단계 결정).
    val faithfulnessFewshot: Boolean = false,
)
