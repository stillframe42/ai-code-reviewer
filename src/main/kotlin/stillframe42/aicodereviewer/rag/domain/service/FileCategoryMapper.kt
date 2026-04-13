package stillframe42.aicodereviewer.rag.domain.service

import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.ARCH
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.API
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.SECURITY
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.STYLE

// 파일 경로 → ConventionCategory 매핑 — 순수 도메인 로직, 외부 의존성 없음
// 파일명 패턴을 우선순위 순서로 매칭한다: SECURITY > API > ARCH > STYLE
object FileCategoryMapper {

    private val securityPattern = Regex("(?i)security|auth|jwt|oauth|filter|interceptor")
    private val apiPattern = Regex("(?i)controller|api|rest|handler")
    private val archPattern = Regex("(?i)service|usecase|repository|dao|entity|config|properties")

    fun selectCategory(filePath: String): ConventionCategory {
        val fileName = filePath.substringAfterLast("/")
        return when {
            fileName.contains(securityPattern) -> SECURITY
            fileName.contains(apiPattern) -> API
            fileName.contains(archPattern) -> ARCH
            else -> STYLE
        }
    }
}
