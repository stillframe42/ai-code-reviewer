package stillframe42.aicodereviewer.rag.adapter.`in`.web

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionIndexUseCase

// 컨벤션 인덱스 관리 엔드포인트 — 내부 운영 전용
// /internal 경로는 외부 노출을 지양하는 관리용 API임을 명시
@RestController
@RequestMapping("/internal/conventions")
class ConventionAdminController(
    private val conventionIndexUseCase: ConventionIndexUseCase,
) {

    // 기존 인덱스를 전체 삭제하고 재인덱싱한다.
    // 컨벤션 문서가 변경된 경우 수동으로 호출한다.
    @PostMapping("/reindex")
    fun reindex(): ResponseEntity<Unit> {
        conventionIndexUseCase.reindex()
        return ResponseEntity.ok().build()
    }
}
