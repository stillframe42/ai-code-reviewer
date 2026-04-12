package stillframe42.aicodereviewer.rag

// 컨벤션 검색 품질 평가에 사용하는 표준 테스트 질문 목록
// LABELED  — (레이블, 질문) 쌍: 검색 결과 리포트 등 라벨이 필요한 곳에서 사용
// QUERIES  — 질문 텍스트만: 청킹·임베딩 실험처럼 라벨 없이 순서만 필요한 곳에서 사용
object ConventionTestQueries {

    val LABELED: List<Pair<String, String>> = listOf(
        "Q1" to "Kotlin data class를 Entity로 쓰면 안 되는 이유는?",
        "Q2" to "Spring에서 @Transactional 범위는 어떻게 잡아야 해?",
        "Q3" to "API 응답에 null을 그대로 내려도 되나?",
        "Q4" to "로깅할 때 개인정보는 어떻게 처리해?",
        "Q5" to "N+1 쿼리 문제 해결 방법은?",
        "Q6" to "Kotlin에서 null 안전성을 처리하는 패턴은?",
        "Q7" to "헥사고날 아키텍처에서 의존성 방향 규칙은?",
        "Q8" to "OWASP Top 10에서 Injection 공격을 방어하는 방법은?",
        "Q9" to "코루틴에서 단일 응답과 스트리밍을 어떻게 구분하나?",
        "Q10" to "use-site target을 명시해야 하는 경우는?",
    )

    val QUERIES: List<String> = LABELED.map { it.second }

    // diff에서 추출한 코드 용어 형태의 쿼리 — 벡터 검색의 약점(약어/코드 식별자)을 검증하기 위한 보조 셋
    // DAY 9 하이브리드 검색 비교 실험에서 기존 LABELED 10개와 함께 사용한다.
    val EDGE_CASE_QUERIES: List<Pair<String, String>> = listOf(
        "E1" to "OWASP A03 Injection",
        "E2" to "PreparedStatement SQL",
        "E3" to "data class Entity JPA",
        "E4" to "JWT authentication filter",
        "E5" to "@Transactional readOnly",
    )
}
