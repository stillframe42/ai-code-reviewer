package stillframe42.aicodereviewer.review.benchmark

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

// v2 프롬프트 벤치마크 — 카테고리/심각도 정의 추가, few-shot 미포함
@SpringBootTest
@TestPropertySource(properties = ["app.prompt.review-system=classpath:prompts/review-system-v2.st"])
class PromptV2BenchmarkTest : AbstractVersionBenchmark() {
    override val version = "v2"
}
