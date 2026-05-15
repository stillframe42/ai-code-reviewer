package stillframe42.aicodereviewer.agent.domain.exception

// Python agent 가 응답 불가 상태일 때 발생 — 네트워크 실패, 5xx 응답 등.
// PythonAgentClient 가 WebClient 예외를 이 타입으로 매핑한다.
class AgentUnavailableException(
    reason: String,
    cause: Throwable? = null,
) : AgentException("Python agent unavailable: $reason", cause)
