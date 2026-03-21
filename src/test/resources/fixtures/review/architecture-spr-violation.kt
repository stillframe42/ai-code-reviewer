// 픽스처: SRP(단일 책임 원칙) 위반 — ARCHITECTURE/MAJOR 이슈 감지 테스트용
// 기대: ARCHITECTURE 카테고리, MAJOR 이상 심각도 이슈 최소 1개

@Service
class UserRegistrationService(
    private val jdbcTemplate: JdbcTemplate,           // DB 직접 접근
    private val restTemplate: RestTemplate,           // HTTP 클라이언트 직접 사용
    private val javaMailSender: JavaMailSender,       // 이메일 발송
    private val smsApiUrl: String,                    // SMS API URL 직접 관리
) {

    // 문제: 단일 클래스가 DB 저장 + 외부 API 호출 + 이메일 + SMS 모두 처리 (SRP 위반)
    fun registerUser(username: String, email: String, phone: String): Long {
        // 책임 1: DB에 직접 사용자 저장 (Repository 없음)
        val userId = jdbcTemplate.queryForObject(
            "INSERT INTO users (username, email, phone) VALUES (?, ?, ?) RETURNING id",
            Long::class.java,
            username, email, phone
        )!!

        // 책임 2: 외부 이메일 인증 API 호출
        val verifyRequest = mapOf("email" to email, "userId" to userId)
        restTemplate.postForObject(
            "https://email-verify-api.internal/send",
            verifyRequest,
            String::class.java
        )

        // 책임 3: 이메일 직접 발송
        val message = SimpleMailMessage()
        message.setTo(email)
        message.subject = "회원가입을 환영합니다"
        message.text = "안녕하세요 $username 님, 가입이 완료되었습니다."
        javaMailSender.send(message)

        // 책임 4: SMS 발송 (RestTemplate으로 직접 외부 API 호출)
        val smsRequest = mapOf("to" to phone, "message" to "[가입완료] $username 님 환영합니다")
        restTemplate.postForObject(smsApiUrl, smsRequest, String::class.java)

        // 책임 5: 포인트 적립 (DB 직접 조작)
        jdbcTemplate.update(
            "INSERT INTO user_points (user_id, points, reason) VALUES (?, ?, ?)",
            userId, 1000, "회원가입 보너스"
        )

        return userId!!
    }
}
