package stillframe42.aicodereviewer.rag.domain.port.`in`

// 컨벤션 인덱싱 인바운드 포트
// index(): 테이블이 비어 있을 때만 인덱싱 (조건부)
// reindex(): 기존 데이터를 삭제하고 강제 재인덱싱
interface ConventionIndexUseCase {
    fun index()
    fun reindex()
}
