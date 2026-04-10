package stillframe42.aicodereviewer.rag.adapter.out.ai

// 청킹 전략 구현체들이 공유하는 컨벤션 파일 경로 → 카테고리 매핑
// DocumentPreprocessor, MarkdownHeaderSplitter, OverlappingTokenSplitter에서 참조
internal val conventionFiles = mapOf(
    "conventions/kotlin-style.md" to "STYLE",
    "conventions/architecture-guide.md" to "ARCH",
    "conventions/api-design.md" to "API",
    "conventions/security-checklist.md" to "SECURITY",
)
