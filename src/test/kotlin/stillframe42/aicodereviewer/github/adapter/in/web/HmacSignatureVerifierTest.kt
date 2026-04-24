package stillframe42.aicodereviewer.github.adapter.`in`.web

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.config.GitHubProperties

// HmacSignatureVerifier 단위 테스트 (Spring 컨텍스트 없음)
class HmacSignatureVerifierTest {

    private val secret = "test-webhook-secret"
    private val verifier = HmacSignatureVerifier(
        GitHubProperties(
            app = GitHubProperties.AppProperties(
                privateKeyPath = "dummy.pem",
                appId = 0L,
                webhookSecret = secret,
            ),
        ),
    )

    private fun sign(payload: String): String =
        "sha256=${computeSignature(payload.toByteArray(Charsets.UTF_8), secret)}"

    @Test
    fun `올바른 서명이면 true를 반환한다`() {
        val payload = """{"action":"opened"}"""
        assertTrue(verifier.verify(payload.toByteArray(Charsets.UTF_8), sign(payload)))
    }

    @Test
    fun `잘못된 서명이면 false를 반환한다`() {
        val payload = """{"action":"opened"}"""
        assertFalse(verifier.verify(payload.toByteArray(Charsets.UTF_8), "sha256=invalidsignature"))
    }

    @Test
    fun `서명 헤더가 null이면 false를 반환한다`() {
        assertFalse(verifier.verify("{}".toByteArray(Charsets.UTF_8), null))
    }

    @Test
    fun `sha256= prefix가 없는 서명이면 false를 반환한다`() {
        val payload = """{"action":"opened"}"""
        val signatureWithoutPrefix = sign(payload).removePrefix("sha256=")
        assertFalse(verifier.verify(payload.toByteArray(Charsets.UTF_8), signatureWithoutPrefix))
    }

    @Test
    fun `페이로드가 변조되면 false를 반환한다`() {
        val original = """{"action":"opened"}"""
        val signature = sign(original)
        val tampered = """{"action":"closed"}"""
        assertFalse(verifier.verify(tampered.toByteArray(Charsets.UTF_8), signature))
    }

    @Test
    fun `빈 페이로드도 올바른 서명이면 true를 반환한다`() {
        val payload = ""
        assertTrue(verifier.verify(payload.toByteArray(Charsets.UTF_8), sign(payload)))
    }
}
