package stillframe42.aicodereviewer.e2e.support

// 에이전트 다운 → Spring AI 폴백 PR fixture 묶음.
// SecurityPrFixture / GeneralPrFixture 와 동일 패턴 — 3개 모였으므로 공통 추출은 후속 작업에서 별도 결정.
data class AgentDownPrFixture(
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

        fun load(): AgentDownPrFixture {
            val payload = readResource("/fixtures/webhooks/agent-down-pr.json")
            return AgentDownPrFixture(
                prNumber = 44,
                webhookPayload = payload,
                webhookSignature = WebhookSignatureHelper.sign(payload, WEBHOOK_SECRET),
                prDiff = readResource("/fixtures/diffs/agent-down-security-config.diff"),
                prFilesJson = readResource("/fixtures/diffs/agent-down-pr-files.json"),
                anthropicReviewResponse = readResource("/fixtures/anthropic/anthropic-fallback-response.json"),
            )
        }

        private fun readResource(path: String): String =
            AgentDownPrFixture::class.java.getResourceAsStream(path)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: error("fixture 리소스 미발견: $path")
    }
}
