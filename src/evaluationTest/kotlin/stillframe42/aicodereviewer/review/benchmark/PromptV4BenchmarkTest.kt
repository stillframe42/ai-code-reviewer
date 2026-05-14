package stillframe42.aicodereviewer.review.benchmark

import org.springframework.test.context.TestPropertySource

// v4 프롬프트 벤치마크 — v1 기반에 필수 스키마 규칙(id 형식, line null 처리)만 추가한 버전
@TestPropertySource(properties = ["app.prompt.review-system=classpath:prompts/review/review-system-v4.st"])
class PromptV4BenchmarkTest : AbstractVersionBenchmark() {
    override val version = "v4"
}
