package stillframe42.aicodereviewer.rag.domain.model

// 컨벤션 문서의 카테고리 분류 — 벡터 검색 필터링에 사용된다.
// name 값(STYLE, ARCH, API, SECURITY)이 metadata.category에 저장된 값과 일치해야 한다.
enum class ConventionCategory {
    STYLE, ARCH, API, SECURITY
}
