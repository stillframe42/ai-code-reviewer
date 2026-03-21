package stillframe42.aicodereviewer.review.benchmark

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

// v1 프롬프트 벤치마크 — few-shot 없이 단순 역할 정의만 포함된 버전
@SpringBootTest
@TestPropertySource(properties = ["app.prompt.review-system=classpath:prompts/review-system-v1.st"])
class PromptV1BenchmarkTest : AbstractVersionBenchmark() {
    override val version = "v1"
}
