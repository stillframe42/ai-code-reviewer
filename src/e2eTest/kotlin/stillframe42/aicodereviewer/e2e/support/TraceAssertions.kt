package stillframe42.aicodereviewer.e2e.support

// 분산 trace propagation 헤더 키 집합 (W3C + Langfuse 커스텀) + Remote agent stdout 카운트.
// 사전 점검: 현 상태에서는 양쪽 모두 미구현이라 마커가 0건 — 그 사실 자체를 박제.
// fixup 후: 마커가 1+ 발견되면 propagation 작동의 증거 → @Disabled 테스트 enable 트리거.
object TraceAssertions {

    val TRACE_MARKERS: Set<String> = setOf(
        "traceparent",                       // W3C Trace Context
        "tracestate",                        // W3C Trace Context
        "x-langfuse-trace-id",               // Langfuse 커스텀
        "x-langfuse-parent-observation-id",  // Langfuse 커스텀
        "langfuse-trace",                    // 부분 매칭 (e.g. langfuse-trace-id 변형)
    )

    fun countTraceMarkers(logs: ContainerLogTail): Int =
        logs.snapshot().count { line ->
            val lower = line.lowercase()
            TRACE_MARKERS.any { lower.contains(it) }
        }
}
