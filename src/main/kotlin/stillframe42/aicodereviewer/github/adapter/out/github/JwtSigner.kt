package stillframe42.aicodereviewer.github.adapter.out.github

import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import java.security.PrivateKey
import java.time.Instant
import java.util.Date

// RS256 JWT 서명 유틸 — GitHub App 인증 토큰 발급에 사용한다
@Component
class JwtSigner {

    fun sign(issuer: String, privateKey: PrivateKey, expirySeconds: Long = 600): String {
        val now = Instant.now()
        return Jwts.builder()
            .issuer(issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(expirySeconds)))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact()
    }
}
