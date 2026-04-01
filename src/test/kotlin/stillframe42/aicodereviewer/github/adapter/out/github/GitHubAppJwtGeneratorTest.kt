package stillframe42.aicodereviewer.github.adapter.out.github

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import stillframe42.aicodereviewer.config.GitHubProperties
import stillframe42.aicodereviewer.integration.AbstractIntegrationTest
import java.time.Instant
import java.util.Base64

// GitHubAppJwtGenerator 통합 테스트
// RsaKeyLoader + JwtSigner 조합이 올바른 GitHub App JWT를 생성하는지 검증한다
// 실제 PEM 파일과 GITHUB_APP_ID 환경변수가 설정된 환경에서만 실행된다 (없으면 자동 스킵)
class GitHubAppJwtGeneratorTest : AbstractIntegrationTest() {

    companion object {
        // GITHUB_APP_ID 환경변수를 Spring 프로퍼티에 주입한다
        // AbstractIntegrationTest의 @DynamicPropertySource와 함께 동작 — datasource는 부모가 처리
        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            val appId = System.getenv("GITHUB_APP_ID") ?: "0"
            registry.add("github.app.app-id") { appId }
        }
    }

    @Autowired
    private lateinit var jwtGenerator: GitHubAppJwtGenerator

    @Autowired
    private lateinit var properties: GitHubProperties

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `JWT는 header-payload-signature 세 파트로 구성된다`() {
        val jwt = jwtGenerator.generate()

        assertThat(jwt.split(".")).hasSize(3)
    }

    @Test
    fun `JWT의 issuer는 설정된 App ID와 일치한다`() {
        val jwt = jwtGenerator.generate()

        // JWT payload는 Base64URL 인코딩 — 공개키 없이 디코딩하여 클레임 검증
        val payloadJson = decodeJwtPayload(jwt)
        val claims: Map<String, Any> = objectMapper.readValue(payloadJson)

        // properties.app.appId는 @DynamicPropertySource로 주입된 값 (미설정 시 0)
        assertThat(claims["iss"]).isEqualTo(properties.app.appId.toString())
    }

    @Test
    fun `JWT의 만료 시간은 생성 시점으로부터 약 10분 후다`() {
        val before = Instant.now()
        val jwt = jwtGenerator.generate()
        val after = Instant.now()

        val payloadJson = decodeJwtPayload(jwt)
        val claims: Map<String, Any> = objectMapper.readValue(payloadJson)

        // exp 클레임은 Unix timestamp(초 단위)
        val exp = (claims["exp"] as Int).toLong()
        val expInstant = Instant.ofEpochSecond(exp)

        // 만료는 생성 시점 + 600초 (±5초 오차 허용)
        assertThat(expInstant).isAfter(before.plusSeconds(595))
        assertThat(expInstant).isBefore(after.plusSeconds(605))
    }

    @Test
    fun `JWT의 알고리즘 헤더는 RS256이다`() {
        val jwt = jwtGenerator.generate()

        // JWT header는 Base64URL 인코딩된 첫 번째 파트
        val headerJson = String(Base64.getUrlDecoder().decode(jwt.split(".")[0]))
        val header: Map<String, Any> = objectMapper.readValue(headerJson)

        assertThat(header["alg"]).isEqualTo("RS256")
    }

    // JWT의 두 번째 파트(payload)를 Base64URL 디코딩하여 JSON 문자열로 반환
    private fun decodeJwtPayload(jwt: String): String {
        val payloadBase64 = jwt.split(".")[1]
        // Base64URL은 패딩(=)이 없을 수 있으므로 4의 배수로 보정
        val padded = payloadBase64.padEnd((payloadBase64.length + 3) / 4 * 4, '=')
        return String(Base64.getUrlDecoder().decode(padded))
    }
}
