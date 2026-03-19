package stillframe42.aicodereviewer.review.domain.model

// 코드 이슈 카테고리
enum class IssueCategory {
    PERFORMANCE,  // N+1 쿼리, 비효율적 시간복잡도, 캐싱 누락, 불필요한 DB 호출
    SECURITY,     // SQL Injection, 인증/인가 누락, 민감정보 노출, 입력값 미검증
    READABILITY,  // 불명확한 네이밍, 함수 50줄 초과, 매직넘버, 중복 코드
    ARCHITECTURE  // SRP 위반, 레이어 의존성 역전, Bean 생명주기 오용, 과도한 결합
}
