package stillframe42.aicodereviewer.github.adapter.out.github

import io.jsonwebtoken.Jwts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.Date

// JwtSigner 단위 테스트 — PEM 파일 없이 인메모리 RSA 키로 JWT 서명 로직을 검증한다
class JwtSignerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var jwtSigner: JwtSigner

    // 테스트용 인메모리 RSA 키쌍 (PEM 파일 불필요)
    private val keyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()

    @Test
    fun `sign 호출 시 issuer가 JWT payload에 포함된다`() {
        val jwt = jwtSigner.sign(
            issuer = "test-issuer-99",
            privateKey = keyPair.private,
        )

        val claims = Jwts.parser()
            .verifyWith(keyPair.public)
            .build()
            .parseSignedClaims(jwt)

        assertThat(claims.payload.issuer).isEqualTo("test-issuer-99")
    }

    @Test
    fun `sign 호출 시 expirySeconds 후에 만료되도록 설정된다`() {
        val expirySeconds = 300L
        val before = Date.from(Instant.now().plusSeconds(expirySeconds - 5))
        val after = Date.from(Instant.now().plusSeconds(expirySeconds + 5))

        val jwt = jwtSigner.sign(
            issuer = "test-issuer",
            privateKey = keyPair.private,
            expirySeconds = expirySeconds,
        )

        val claims = Jwts.parser()
            .verifyWith(keyPair.public)
            .build()
            .parseSignedClaims(jwt)

        assertThat(claims.payload.expiration).isAfter(before).isBefore(after)
    }

    @Test
    fun `expirySeconds 기본값은 600초다`() {
        val before = Date.from(Instant.now().plusSeconds(595))
        val after = Date.from(Instant.now().plusSeconds(605))

        val jwt = jwtSigner.sign(
            issuer = "test-issuer",
            privateKey = keyPair.private,
            // expirySeconds 생략 → 기본값 600
        )

        val claims = Jwts.parser()
            .verifyWith(keyPair.public)
            .build()
            .parseSignedClaims(jwt)

        assertThat(claims.payload.expiration).isAfter(before).isBefore(after)
    }
}
