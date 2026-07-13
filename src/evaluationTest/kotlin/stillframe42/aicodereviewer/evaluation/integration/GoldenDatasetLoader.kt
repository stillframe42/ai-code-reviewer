package stillframe42.aicodereviewer.evaluation.integration

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import stillframe42.aicodereviewer.evaluation.domain.model.GoldenCase

// 골든 데이터셋(snake_case JSON) → GoldenCase(camelCase) 역직렬화 전용 로더
// 도메인 모델의 @JsonProperty 제거(f51bfc5) 이후 명명 전략 매핑은 이 로더가 책임진다 —
// 컨텍스트의 공용 ObjectMapper 빈은 camelCase 라 이 데이터셋 파싱에 쓸 수 없다
object GoldenDatasetLoader {

    private val mapper = jacksonObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    fun load(): List<GoldenCase> {
        val json = javaClass.classLoader
            .getResourceAsStream("fixtures/evaluation/golden-dataset.json")!!
            .bufferedReader().readText()
        val root = mapper.readTree(json)
        return mapper.readValue(root["cases"].toString())
    }
}
