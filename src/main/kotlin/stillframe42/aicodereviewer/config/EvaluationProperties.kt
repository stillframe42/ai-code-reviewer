package stillframe42.aicodereviewer.config

import org.springframework.boot.context.properties.ConfigurationProperties

// 평가(LLM-as-a-Judge) 관련 설정 — app.rag.evaluation.* 프로퍼티에 바인딩
@ConfigurationProperties(prefix = "app.rag.evaluation")
data class EvaluationProperties(
    // 평가용 LLM 모델명 (비용 절감을 위해 gpt-4o-mini 기본값)
    // Sub-plan A 단계 2/3 측정 시 gpt-4o로 오버라이드 가능 (env var APP_RAG_EVALUATION_MODEL=gpt-4o)
    val model: String = "gpt-4o-mini",
    // Faithfulness 평가에 사용할 프롬프트 Classpath 경로.
    // Sub-plan A는 boolean 토글이었으나 D에서 경로 property로 전환 — variant 확장성 확보.
    // Default = 원본 프롬프트 (production 동작 보존). A 재현 시 classpath:prompts/evaluation-faithfulness-fewshot.st.
    // D variants: classpath:prompts/evaluation-faithfulness-d-v{1,2,3}.st.
    val faithfulnessPrompt: String = "classpath:prompts/evaluation-faithfulness.st",
)
