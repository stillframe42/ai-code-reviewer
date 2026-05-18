package stillframe42.aicodereviewer.e2e.support

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// GitHub Webhook 서명(X-Hub-Signature-256) 계산 헬퍼.
// 운영 코드의 internal computeSignature 와 동일 알고리즘 — e2eTest source set 이 friendModule 로 등록되지 않아 직접 구현.
// 알고리즘이 GitHub 표준이라 drift 위험 낮음.
object WebhookSignatureHelper {

    private const val ALGORITHM = "HmacSHA256"
    private const val PREFIX = "sha256="

    fun sign(payload: String, secret: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM))
        val hex = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return PREFIX + hex
    }
}
