package stillframe42.aicodereviewer.common

import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import java.security.PrivateKey
import java.time.Instant
import java.util.Date

// RS256 JWT 서명 공통 유틸 — issuer, key, expiry를 파라미터로 받아 서명된 JWT를 반환한다
// 특정 서비스에 종속되지 않으며 RSA 키 기반 JWT가 필요한 모든 곳에서 재사용 가능하다
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
