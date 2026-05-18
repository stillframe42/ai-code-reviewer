package stillframe42.aicodereviewer.e2e.support

// 일반 PR fixture 묶음. companion.load() 가 classpath 에서 모두 로드 + 서명 계산.
// SecurityPrFixture 와 동일 패턴의 별도 data class — 같은 형태 3개 모이면 공통 추출 검토 (rule of three).
data class GeneralPrFixture(
    val prNumber: Int,
    val webhookPayload: String,
    val webhookSignature: String,
    val prDiff: String,
    val prFilesJson: String,
    val anthropicReviewResponse: String,
) {
    companion object {
        // application-e2e-test.yml 의 github.app.webhook-secret 와 동일해야 함
        private const val WEBHOOK_SECRET = "e2e-webhook-secret"

        fun load(): GeneralPrFixture {
            val payload = readResource("/fixtures/webhooks/general-pr.json")
            return GeneralPrFixture(
                prNumber = 43,
                webhookPayload = payload,
                webhookSignature = WebhookSignatureHelper.sign(payload, WEBHOOK_SECRET),
                prDiff = readResource("/fixtures/diffs/general-userservice.diff"),
                prFilesJson = readResource("/fixtures/diffs/general-pr-files.json"),
                anthropicReviewResponse = readResource("/fixtures/anthropic/anthropic-review-response.json"),
            )
        }

        private fun readResource(path: String): String =
            GeneralPrFixture::class.java.getResourceAsStream(path)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: error("fixture 리소스 미발견: $path")
    }
}
