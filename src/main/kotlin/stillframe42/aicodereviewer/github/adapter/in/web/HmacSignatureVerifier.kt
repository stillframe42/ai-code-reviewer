package stillframe42.aicodereviewer.github.adapter.`in`.web

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.config.GitHubProperties
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// HMAC 알고리즘 식별자
private const val HMAC_ALGORITHM = "HmacSHA256"

// GitHub X-Hub-Signature-256 헤더의 고정 prefix
private const val SIGNATURE_PREFIX = "sha256="

// GitHub Webhook HMAC-SHA256 서명 검증 컴포넌트
@Component
class HmacSignatureVerifier(private val properties: GitHubProperties) {

    fun verify(payload: ByteArray, signatureHeader: String?): Boolean {
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) return false

        val expected = signatureHeader.removePrefix(SIGNATURE_PREFIX)
        val actual = computeSignature(payload, properties.app.webhookSecret)

        // 상수 시간 비교 — 타이밍 공격 방지
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8),
        )
    }
}

// HMAC-SHA256 서명을 hex 문자열로 반환한다.
// 테스트 코드에서 서명 생성 용도로 재사용하기 위해 internal로 노출한다.
internal fun computeSignature(data: ByteArray, secret: String): String {
    val mac = Mac.getInstance(HMAC_ALGORITHM)
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
    return mac.doFinal(data).joinToString("") { "%02x".format(it) }
}
