package stillframe42.aicodereviewer.review.domain.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.review.domain.model.FileReviewStrategy

// FileExtensionClassifier 순수 단위 테스트 — Spring 컨텍스트 없이 도메인 로직만 검증
class FileExtensionClassifierTest {

    // ─── FullReview 대상 ─────────────────────────────────────────────────────

    @Test
    fun `kt 확장자는 FullReview`() {
        assertEquals(FileReviewStrategy.FullReview, FileExtensionClassifier.classify("Foo.kt"))
    }

    @Test
    fun `java 확장자는 FullReview`() {
        assertEquals(FileReviewStrategy.FullReview, FileExtensionClassifier.classify("Main.java"))
    }

    @Test
    fun `ts 확장자는 FullReview`() {
        assertEquals(FileReviewStrategy.FullReview, FileExtensionClassifier.classify("app.ts"))
    }

    @Test
    fun `py 확장자는 FullReview`() {
        assertEquals(FileReviewStrategy.FullReview, FileExtensionClassifier.classify("script.py"))
    }

    // ─── QueryReview 대상 ────────────────────────────────────────────────────

    @Test
    fun `yml 확장자는 QueryReview`() {
        assertEquals(FileReviewStrategy.QueryReview, FileExtensionClassifier.classify("application.yml"))
    }

    @Test
    fun `yaml 확장자는 QueryReview`() {
        assertEquals(FileReviewStrategy.QueryReview, FileExtensionClassifier.classify("config.yaml"))
    }

    @Test
    fun `xml 확장자는 QueryReview`() {
        assertEquals(FileReviewStrategy.QueryReview, FileExtensionClassifier.classify("pom.xml"))
    }

    @Test
    fun `json 확장자는 QueryReview`() {
        assertEquals(FileReviewStrategy.QueryReview, FileExtensionClassifier.classify("package.json"))
    }

    @Test
    fun `gradle 확장자는 QueryReview`() {
        assertEquals(FileReviewStrategy.QueryReview, FileExtensionClassifier.classify("build.gradle"))
    }

    @Test
    fun `kts 확장자는 QueryReview`() {
        assertEquals(FileReviewStrategy.QueryReview, FileExtensionClassifier.classify("build.gradle.kts"))
    }

    @Test
    fun `sql 확장자는 QueryReview`() {
        assertEquals(FileReviewStrategy.QueryReview, FileExtensionClassifier.classify("V1__init.sql"))
    }

    @Test
    fun `md 확장자는 QueryReview`() {
        assertEquals(FileReviewStrategy.QueryReview, FileExtensionClassifier.classify("README.md"))
    }

    @Test
    fun `sh 확장자는 QueryReview`() {
        assertEquals(FileReviewStrategy.QueryReview, FileExtensionClassifier.classify("deploy.sh"))
    }

    @Test
    fun `Dockerfile 파일명은 QueryReview`() {
        assertEquals(FileReviewStrategy.QueryReview, FileExtensionClassifier.classify("Dockerfile"))
    }

    // ─── Skip 대상 ───────────────────────────────────────────────────────────

    @Test
    fun `png 확장자는 Skip`() {
        assertEquals(FileReviewStrategy.Skip, FileExtensionClassifier.classify("logo.png"))
    }

    @Test
    fun `jar 확장자는 Skip`() {
        assertEquals(FileReviewStrategy.Skip, FileExtensionClassifier.classify("lib.jar"))
    }

    @Test
    fun `woff 확장자는 Skip`() {
        assertEquals(FileReviewStrategy.Skip, FileExtensionClassifier.classify("font.woff"))
    }

    @Test
    fun `gradlew 파일명은 Skip`() {
        assertEquals(FileReviewStrategy.Skip, FileExtensionClassifier.classify("gradlew"))
    }

    @Test
    fun `gradlew_bat 파일명은 Skip`() {
        assertEquals(FileReviewStrategy.Skip, FileExtensionClassifier.classify("gradlew.bat"))
    }

    // ─── 경로 처리 ───────────────────────────────────────────────────────────

    @Test
    fun `경로 포함 yml 파일명은 QueryReview`() {
        assertEquals(
            FileReviewStrategy.QueryReview,
            FileExtensionClassifier.classify("src/main/resources/application.yml"),
        )
    }

    @Test
    fun `경로 포함 kt 파일명은 FullReview`() {
        assertEquals(
            FileReviewStrategy.FullReview,
            FileExtensionClassifier.classify("src/main/kotlin/com/example/Foo.kt"),
        )
    }

    @Test
    fun `경로에 Dockerfile이 있어도 파일명이 Dockerfile이면 QueryReview`() {
        assertEquals(
            FileReviewStrategy.QueryReview,
            FileExtensionClassifier.classify("docker/Dockerfile"),
        )
    }

    // ─── 대소문자 무관 처리 ──────────────────────────────────────────────────

    @Test
    fun `대문자 YML 확장자는 QueryReview`() {
        assertEquals(FileReviewStrategy.QueryReview, FileExtensionClassifier.classify("CONFIG.YML"))
    }

    @Test
    fun `대문자 KT 확장자는 FullReview`() {
        assertEquals(FileReviewStrategy.FullReview, FileExtensionClassifier.classify("Foo.KT"))
    }

    // ─── extractExtension 테스트 ─────────────────────────────────────────────

    @Test
    fun `중첩 확장자는 마지막 확장자만 반환`() {
        assertEquals("kts", FileExtensionClassifier.extractExtension("build.gradle.kts"))
    }

    @Test
    fun `확장자 없는 파일명은 빈 문자열 반환`() {
        assertEquals("", FileExtensionClassifier.extractExtension("Dockerfile"))
    }

    @Test
    fun `경로 포함 파일명에서 확장자 추출`() {
        assertEquals("yml", FileExtensionClassifier.extractExtension("src/main/resources/application.yml"))
    }
}
