package stillframe42.aicodereviewer.review.benchmark

import java.util.concurrent.CopyOnWriteArrayList

// 버전별 벤치마크 결과를 JVM 전역에서 공유하는 싱글톤 저장소
// 3개 버전 클래스가 각자 결과를 추가하고, 마지막 버전의 @AfterAll에서 통합 표를 출력함
object BenchmarkResultStore {

    // 멀티스레드 환경에서 안전한 리스트 (Gradle 병렬 테스트 대비)
    val results: MutableList<PromptBenchmarkResult> = CopyOnWriteArrayList()

    // 등록된 버전 추적 — 마지막 버전인지 판단에 사용
    private val completedVersions = CopyOnWriteArrayList<String>()
    private val allVersions = listOf("v1", "v2", "v3", "v4")

    fun addResult(result: PromptBenchmarkResult) {
        results.add(result)
    }

    fun markVersionCompleted(version: String) {
        completedVersions.add(version)
    }

    fun isLastVersion(): Boolean = completedVersions.containsAll(allVersions)

    fun printCombinedSummary() {
        if (results.isEmpty()) return

        val tokenByVersion = results
            .distinctBy { it.version }
            .associate { it.version to it.systemPromptTokenEstimate }

        println()
        println("╔══════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗")
        println("║                              프롬프트 버전별 벤치마크 통합 결과                                                  ║")
        println("╚══════════════════════════════════════════════════════════════════════════════════════════════════════════════════╝")

        val tokenSummary = allVersions.joinToString(" / ") { v ->
            "$v=${tokenByVersion[v] ?: "-"}"
        }
        println("시스템 프롬프트 토큰 추정: $tokenSummary")
        println()

        println(
            "%-4s | %-38s | %4s | %5s | %4s | %3s | %4s | %4s | %4s | %6s | %s".format(
                "버전", "픽스처", "점수", "이슈수", "CRIT", "SEC", "PERF", "READ", "ARCH", "응답시간", "필드완전"
            )
        )
        println("-".repeat(112))

        // 픽스처별 → 버전별 정렬하여 비교하기 쉽게 출력
        results
            .sortedWith(compareBy({ it.fixtureName }, { it.version }))
            .forEach { r ->
                println(
                    "%-4s | %-38s | %4d | %5d | %4d | %3d | %4d | %4d | %4d | %5dms | %s".format(
                        r.version,
                        r.fixtureName,
                        r.overallScore,
                        r.totalIssues,
                        r.criticalCount,
                        r.securityCount,
                        r.performanceCount,
                        r.readabilityCount,
                        r.architectureCount,
                        r.durationMs,
                        if (r.allIssueFieldsPopulated) "O" else "X",
                    )
                )
            }
        println()
    }
}
