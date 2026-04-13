package stillframe42.aicodereviewer.rag.domain.service

import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.API
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.ARCH
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.SECURITY
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.STYLE

// 파일 경로 → ConventionCategory 매핑 — 순수 도메인 로직, 외부 의존성 없음
// 파일명 패턴을 우선순위 순서로 매칭한다: SECURITY > API > ARCH > STYLE
object FileCategoryMapper {

    // Auth·Rest는 오탐(AuthorService, Restaurant) 방지를 위해 다음 글자가 대문자인 경우만 매칭
    private val authBoundary = Regex("Auth[A-Z]")
    private val restBoundary = Regex("Rest[A-Z]")

    fun selectCategory(filePath: String): ConventionCategory {
        val fileName = filePath.substringAfterLast("/")
        val lowerFileName = fileName.lowercase()

        return when {
            // SECURITY: Filter는 Spring Security 필터(OncePerRequestFilter 등)와 동의어로 보아
            // 단어 경계 없이 전체 파일명에서 매칭한다 (DataFilter → SECURITY 의도된 동작)
            lowerFileName.contains("security") ||
            lowerFileName.contains("jwt") ||
            lowerFileName.contains("oauth") ||
            lowerFileName.contains("filter") ||
            lowerFileName.contains("interceptor") ||
            fileName.contains(authBoundary) -> SECURITY

            // API: Rest는 Restaurant 오탐 방지를 위해 다음 글자가 대문자인 경우만 매칭
            lowerFileName.contains("controller") ||
            lowerFileName.contains("api") ||
            lowerFileName.contains("handler") ||
            fileName.contains(restBoundary) -> API

            // ARCH: 레이어 경계·트랜잭션·의존성 방향 컨벤션 대상
            lowerFileName.contains("service") ||
            lowerFileName.contains("usecase") ||
            lowerFileName.contains("repository") ||
            lowerFileName.contains("dao") ||
            lowerFileName.contains("entity") ||
            lowerFileName.contains("config") ||
            lowerFileName.contains("properties") -> ARCH

            else -> STYLE
        }
    }
}
