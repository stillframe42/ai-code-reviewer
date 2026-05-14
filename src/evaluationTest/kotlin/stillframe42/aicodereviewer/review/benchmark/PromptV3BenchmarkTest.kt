package stillframe42.aicodereviewer.review.benchmark

import org.springframework.test.context.TestPropertySource

// v3 프롬프트 벤치마크 — few-shot 예시 포함, 토큰 최적화 적용 버전
@TestPropertySource(properties = ["app.prompt.review-system=classpath:prompts/review/review-system-v3.st"])
class PromptV3BenchmarkTest : AbstractVersionBenchmark() {
    override val version = "v3"
}
