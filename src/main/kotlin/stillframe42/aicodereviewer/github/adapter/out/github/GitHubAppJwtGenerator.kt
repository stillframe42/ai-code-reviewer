package stillframe42.aicodereviewer.github.adapter.out.github

import org.springframework.stereotype.Component
import stillframe42.aicodereviewer.common.JwtSigner
import stillframe42.aicodereviewer.common.RsaKeyLoader
import stillframe42.aicodereviewer.config.GitHubProperties
import java.security.PrivateKey

// GitHub App 전용 JWT 생성기
// RsaKeyLoader와 JwtSigner를 조합하여 GitHub App 인증에 필요한 JWT를 생성한다
@Component
class GitHubAppJwtGenerator(
    private val rsaKeyLoader: RsaKeyLoader,
    private val jwtSigner: JwtSigner,
    private val properties: GitHubProperties,
) {
    // PEM 파일은 첫 호출 시점에 한 번만 로드한다 (Bean 생성 시 파일 불필요)
    private val privateKey: PrivateKey by lazy {
        rsaKeyLoader.load(properties.app.privateKeyPath)
    }

    fun generate(): String =
        jwtSigner.sign(
            issuer = properties.app.appId.toString(),
            privateKey = privateKey,
            expirySeconds = 600,  // GitHub App JWT 유효 시간: 10분
        )
}
