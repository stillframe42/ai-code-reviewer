package stillframe42.aicodereviewer.agent.domain.exception

// 원격 agent 가 응답 불가 상태일 때 발생 — 네트워크 실패, 5xx 응답 등.
// RemoteAgentClient 가 HTTP 클라이언트 예외를 이 타입으로 매핑한다.
class AgentUnavailableException(
    reason: String,
    cause: Throwable? = null,
) : AgentException("Remote agent unavailable: $reason", cause)
