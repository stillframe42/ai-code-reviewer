package stillframe42.aicodereviewer.github.adapter.`in`.web

import tools.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import stillframe42.aicodereviewer.github.adapter.`in`.web.dto.WebhookPayloadDto
import stillframe42.aicodereviewer.github.domain.port.`in`.GitHubWebhookUseCase

// GitHub App Webhook 수신 컨트롤러
// 서명 검증 → 이벤트 필터링 → fire-and-forget 처리 흐름을 담당한다
@RestController
@RequestMapping("/api/github")
class WebhookController(
    private val gitHubWebhookUseCase: GitHubWebhookUseCase,
    private val signatureVerifier: HmacSignatureVerifier,
    private val objectMapper: ObjectMapper,
    @param:Qualifier("applicationScope") private val applicationScope: CoroutineScope,
) {

    private val logger = LoggerFactory.getLogger(WebhookController::class.java)

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestBody body: String,
        @RequestHeader("X-Hub-Signature-256", required = false) signature: String?,
        @RequestHeader("X-GitHub-Event", required = false) eventType: String?,
    ): ResponseEntity<Unit> {
        // 1단계: HMAC-SHA256 서명 검증
        if (!signatureVerifier.verify(body.toByteArray(Charsets.UTF_8), signature)) {
            logger.warn("Webhook 서명 검증 실패: event={}", eventType)
            return ResponseEntity.status(401).build()
        }

        // 2단계: pull_request 이벤트만 처리, 나머지는 무시
        if (eventType != "pull_request") {
            logger.debug("지원하지 않는 이벤트 타입 무시: {}", eventType)
            return ResponseEntity.ok().build()
        }

        // 3단계: JSON 파싱 및 도메인 변환
        val dto = objectMapper.readValue(body, WebhookPayloadDto::class.java)
        val event = dto.toDomain()

        if (event == null) {
            logger.debug("지원하지 않는 PR action 무시: {}", dto.action)
            return ResponseEntity.ok().build()
        }

        logger.info(
            "Webhook 수신: event={}, action={}, repo={}, pr={}",
            eventType, event.action, event.repositoryFullName, event.pullRequestNumber,
        )

        // 4단계: fire-and-forget — 즉시 202 반환 후 백그라운드에서 AI 리뷰 처리
        applicationScope.launch {
            runCatching { gitHubWebhookUseCase.handlePullRequestEvent(event) }
                .onFailure {
                    logger.error(
                        "PR 이벤트 처리 실패: repo={}, pr={}",
                        event.repositoryFullName, event.pullRequestNumber, it,
                    )
                }
        }

        return ResponseEntity.accepted().build()
    }
}
