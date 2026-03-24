package stillframe42.aicodereviewer.github.adapter.`in`.web

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.config.GitHubProperties
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// GitHub Webhook HMAC-SHA256 서명 검증 컴포넌트
@Component
class HmacSignatureVerifier(private val properties: GitHubProperties) {

    // GitHub가 전송한 X-Hub-Signature-256 헤더와 페이로드를 검증한다
    // MessageDigest.isEqual()로 타이밍 공격을 방지한다
    fun verify(payload: ByteArray, signatureHeader: String?): Boolean {
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) return false

        val expected = signatureHeader.removePrefix("sha256=")
        val actual = computeSignature(payload, properties.app.webhookSecret)

        // 상수 시간 비교 — 타이밍 공격 방지
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )
    }

    companion object {
        // 테스트 코드에서 재사용할 수 있도록 internal로 노출
        internal fun computeSignature(data: ByteArray, secret: String): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
            return mac.doFinal(data).joinToString("") { "%02x".format(it) }
        }
    }
}
