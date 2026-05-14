package stillframe42.aicodereviewer.common.exception

// 리소스 단건 조회 결과 미존재를 표현 — GlobalExceptionHandler 가 404 로 매핑한다
class NotFoundException(message: String) : RuntimeException(message)
