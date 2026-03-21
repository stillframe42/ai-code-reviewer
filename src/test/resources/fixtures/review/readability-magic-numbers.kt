// 픽스처: 매직넘버/불명확한 네이밍/긴 함수 — READABILITY 이슈 감지 테스트용
// 기대: READABILITY 카테고리 이슈 최소 2개

@Service
class PricingService {

    // 문제 1: 의미 없는 매직넘버들 (0.1, 0.05, 0.15, 100, 1000, 50)
    fun calculate(x: Double, q: Int, t: String): Double {
        var p = x * q

        // 문제 2: 변수명 p, x, q, t가 의미 불명확
        if (t == "VIP") {
            p = p * 0.85  // 15% 할인? 85%? 의미 불명확
        } else if (t == "MEMBER") {
            p = p * 0.90
        }

        // 문제 3: 매직넘버로 조건 분기
        if (q > 50) {
            p = p * 0.95
        }

        // 문제 4: 세금 계산인지 불명확한 10% 추가
        if (p > 100000) {
            p = p * 1.10
        }

        // 문제 5: 반올림 소수점 자리 매직넘버
        return Math.round(p * 100.0) / 100.0
    }

    // 문제 6: 50줄에 가까운 긴 함수 + 매직넘버 반복
    fun processOrder(orderId: Long, userId: Long, items: List<Any>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        var subtotal = 0.0
        for (item in items) {
            // 처리 로직
        }

        val shipping = if (subtotal >= 30000) 0.0 else 3000.0  // 매직넘버
        val tax = subtotal * 0.10                               // 매직넘버
        val total = subtotal + shipping + tax

        result["subtotal"] = subtotal
        result["shipping"] = shipping
        result["tax"] = tax
        result["total"] = total
        result["status"] = if (total > 500000) "HIGH_VALUE" else "NORMAL"  // 매직넘버

        return result
    }
}
