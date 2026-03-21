// 픽스처: 하드코딩된 자격증명 — SECURITY/CRITICAL 이슈 감지 테스트용
// 기대: SECURITY 카테고리, CRITICAL 심각도 이슈 최소 1개

@Service
class PaymentService {

    // 취약점 1: API 키 하드코딩
    private val stripeApiKey = "sk_live_4eC39HqLyjWDarjtT1zdp7dc"

    // 취약점 2: DB 비밀번호 하드코딩
    private val dbPassword = "admin1234!"

    // 취약점 3: JWT 시크릿 하드코딩
    private val jwtSecret = "my-super-secret-jwt-key-do-not-share"

    fun processPayment(amount: Long, cardToken: String): Boolean {
        val stripe = StripeClient(stripeApiKey)
        return stripe.charge(amount, cardToken)
    }

    fun getDbConnection(): Connection {
        return DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/prod_db",
            "admin",
            dbPassword
        )
    }
}
