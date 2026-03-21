// 픽스처: 깨끗한 코드 — false positive 측정용
// 기대: CRITICAL/MAJOR 이슈 0개, overall_score 7 이상

@Service
class CurrencyConverter(private val exchangeRateRepository: ExchangeRateRepository) {

    companion object {
        private const val BASE_CURRENCY = "KRW"
        private const val ROUNDING_SCALE = 2
    }

    // 명확한 함수명, 타입 안전 파라미터, 단일 책임
    suspend fun convert(amount: BigDecimal, fromCurrency: String, toCurrency: String): BigDecimal {
        if (fromCurrency == toCurrency) return amount

        val rate = exchangeRateRepository.findRate(from = fromCurrency, to = toCurrency)
            ?: throw CurrencyNotFoundException("환율 정보를 찾을 수 없습니다: $fromCurrency → $toCurrency")

        return amount.multiply(rate).setScale(ROUNDING_SCALE, RoundingMode.HALF_UP)
    }

    // 단일 표현식, 명확한 의도
    suspend fun isSupported(currency: String): Boolean =
        exchangeRateRepository.existsByCurrency(currency)
}
