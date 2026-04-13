package stillframe42.aicodereviewer.rag.domain.service

import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.API
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.ARCH
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.SECURITY
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.STYLE

// 파일 경로 → ConventionCategory 매핑 — 순수 도메인 로직, 외부 의존성 없음
// 파일명 패턴을 우선순위 순서로 매칭한다: SECURITY > API > ARCH > STYLE
object FileCategoryMapper {

    fun selectCategory(filePath: String): ConventionCategory {
        val fileName = filePath.substringAfterLast("/")
        val lowerFileName = fileName.lowercase()

        // suffix 기반 매칭 — camelCase 단어 경계 확인
        return when {
            // SECURITY: Security, Jwt, OAuth, Filter, Interceptor, AuthXxx (Auth 다음이 대문자)
            lowerFileName.contains("security") ||
            lowerFileName.contains("jwt") ||
            lowerFileName.contains("oauth") ||
            lowerFileName.contains("filter") ||
            lowerFileName.contains("interceptor") ||
            fileName.contains(Regex("Auth[A-Z]")) -> SECURITY

            // API: Controller, Api, Handler, RestXxx (Rest 다음이 대문자)
            lowerFileName.contains("controller") ||
            lowerFileName.contains("api") ||
            lowerFileName.contains("handler") ||
            fileName.contains(Regex("Rest[A-Z]")) -> API

            // ARCH: Service, UseCase, Repository, Dao, Entity, Config, Properties
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
