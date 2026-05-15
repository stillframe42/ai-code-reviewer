package stillframe42.aicodereviewer.agent.domain.exception

// Agent 도메인의 모든 예외 베이스 — sealed 로 선언해 호출자가 when 으로
// 모든 분기를 컴파일러 강제로 다룰 수 있게 한다.
sealed class AgentException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
