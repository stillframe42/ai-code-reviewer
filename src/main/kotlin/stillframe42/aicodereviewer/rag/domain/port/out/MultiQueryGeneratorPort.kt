package stillframe42.aicodereviewer.rag.domain.port.out

// 원본 쿼리를 LLM 기반으로 다양한 변형으로 확장하는 아웃바운드 포트.
// 구현체는 외부 LLM 호출/JSON 파싱을 담당하고, 실패 시 graceful degradation 정책은 구현체 책임.
interface MultiQueryGeneratorPort {
    fun generateMultipleQueries(originalQuery: String): List<String>
}
