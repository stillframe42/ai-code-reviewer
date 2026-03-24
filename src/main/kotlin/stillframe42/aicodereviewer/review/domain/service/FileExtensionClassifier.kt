package stillframe42.aicodereviewer.review.domain.service

import stillframe42.aicodereviewer.review.domain.model.FileReviewStrategy

// 파일명(경로 포함)에서 확장자를 추출하여 리뷰 처리 전략을 반환하는 도메인 서비스
// 상태 없는 순수 분류 함수이므로 object(싱글톤)로 선언한다
// 외부 설정 연동이 필요해지면 @Component 클래스로 전환한다
object FileExtensionClassifier {

    // 리뷰에서 완전히 제외할 확장자 — 이미지, 바이너리, 빌드 산출물
    private val SKIP_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "svg", "ico", "webp",  // 이미지
        "woff", "woff2", "ttf", "eot",                       // 폰트
        "pdf", "docx", "xlsx",                               // 문서
        "jar", "class", "war",                               // JVM 빌드 산출물
        "map",                                               // 소스맵
        "zip", "tar", "gz",                                  // 압축 파일
    )

    // 리뷰에서 완전히 제외할 특수 파일명 (확장자 없음)
    private val SKIP_FILE_NAMES = setOf(
        "gradlew", "gradlew.bat",
    )

    // 변경 의도·구조적 영향 위주로 리뷰할 확장자 — 설정, 스크립트, 마크업
    private val QUERY_REVIEW_EXTENSIONS = setOf(
        "yml", "yaml",      // 설정 파일
        "xml",              // Maven, Spring XML
        "json",             // 설정, 스키마
        "gradle",           // Gradle 스크립트
        "kts",              // Kotlin 스크립트 (build.gradle.kts 등)
        "properties",       // Spring 설정
        "toml",             // 패키지 설정
        "sql",              // DB 스키마·마이그레이션
        "md",               // 문서
        "html",             // 마크업
        "css",              // 스타일시트
        "sh", "bash",       // 쉘 스크립트
        "tf", "tfvars",     // Terraform
    )

    // 변경 의도·구조적 영향 위주로 리뷰할 특수 파일명 (확장자 없음)
    private val QUERY_REVIEW_FILE_NAMES = setOf(
        "Dockerfile",
    )

    // 파일명(경로 포함 가능) → 리뷰 처리 전략 반환
    fun classify(fileName: String): FileReviewStrategy {
        val baseName = fileName.substringAfterLast("/")

        // 특수 파일명 우선 매칭 (확장자 없는 Dockerfile 등)
        if (baseName in SKIP_FILE_NAMES) return FileReviewStrategy.Skip
        if (baseName in QUERY_REVIEW_FILE_NAMES) return FileReviewStrategy.QueryReview

        val extension = extractExtension(fileName)

        return when {
            extension in SKIP_EXTENSIONS         -> FileReviewStrategy.Skip
            extension in QUERY_REVIEW_EXTENSIONS -> FileReviewStrategy.QueryReview
            else                                 -> FileReviewStrategy.FullReview
        }
    }

    // 파일명에서 확장자를 추출한다
    // 경로 구분자 제거 후 마지막 점 이후 문자열을 소문자로 반환한다
    // 예: "build.gradle.kts" → "kts", "Dockerfile" → ""
    internal fun extractExtension(fileName: String): String =
        fileName.substringAfterLast("/")
            .substringAfterLast(".", "")
            .lowercase()
}
