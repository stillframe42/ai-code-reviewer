package stillframe42.aicodereviewer.common.exception

// AI 응답 수신 실패를 표현 — 사용자에게 그대로 노출해도 되는 메시지를 담는다.
// GlobalExceptionHandler 가 500 + message 로 매핑한다 (미분류 예외는 고정 문구로 가려지는 것과 대비)
class AiResponseException(message: String) : RuntimeException(message)
