package stillframe42.aicodereviewer.e2e.support

// 보안 PR (Remote 에이전트 경로) 의 5개 fixture 묶음. companion.load() 가 classpath 에서 모두 로드 + 서명 계산.
data class SecurityPrFixture(
    val prNumber: Int,
    val webhookPayload: String,
    val webhookSignature: String,
    val prDiff: String,
    val prFilesJson: String,
    val openAiToolCallResponse: String,
    val openAiFinalIssuesResponse: String,
) {
    companion object {
        // application-e2e-test.yml 의 github.app.webhook-secret 와 동일해야 함
        private const val WEBHOOK_SECRET = "e2e-webhook-secret"

        fun load(): SecurityPrFixture {
            val payload = readResource("/fixtures/webhooks/security-pr.json")
            return SecurityPrFixture(
                prNumber = 42,
                webhookPayload = payload,
                webhookSignature = WebhookSignatureHelper.sign(payload, WEBHOOK_SECRET),
                prDiff = readResource("/fixtures/diffs/security-config-plaintext.diff"),
                prFilesJson = readResource("/fixtures/diffs/security-pr-files.json"),
                openAiToolCallResponse = readResource("/fixtures/openai/openai-chat-tool-call.json"),
                openAiFinalIssuesResponse = readResource("/fixtures/openai/openai-chat-final-issues.json"),
            )
        }

        private fun readResource(path: String): String =
            SecurityPrFixture::class.java.getResourceAsStream(path)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: error("fixture 리소스 미발견: $path")
    }
}
