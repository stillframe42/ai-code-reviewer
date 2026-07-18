package stillframe42.aicodereviewer.github.adapter.`in`.web

import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import stillframe42.aicodereviewer.common.Logging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import stillframe42.aicodereviewer.github.adapter.`in`.web.dto.WebhookPayloadDto
import stillframe42.aicodereviewer.github.domain.port.`in`.GitHubWebhookUseCase
import java.util.concurrent.ExecutorService

// GitHub App Webhook 수신 컨트롤러
// 서명 검증 → 이벤트 필터링 → fire-and-forget 처리 흐름을 담당한다
@RestController
@RequestMapping("/api/github")
class WebhookController(
    private val gitHubWebhookUseCase: GitHubWebhookUseCase,
    private val signatureVerifier: HmacSignatureVerifier,
    private val objectMapper: ObjectMapper,
    @param:Qualifier("applicationExecutor") private val applicationExecutor: ExecutorService,
) : Logging {

    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestBody body: String,
        @RequestHeader("X-Hub-Signature-256", required = false) signature: String?,
        @RequestHeader("X-GitHub-Event", required = false) eventType: String?,
    ): ResponseEntity<Unit> {
        // 1단계: HMAC-SHA256 서명 검증
        if (!signatureVerifier.verify(body.toByteArray(Charsets.UTF_8), signature)) {
            logger.warn("Webhook 서명 검증 실패: event={}", eventType)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        // 2단계: pull_request 이벤트만 처리, 나머지는 무시
        if (eventType != "pull_request") {
            logger.debug("지원하지 않는 이벤트 타입 무시: {}", eventType)
            return ResponseEntity.ok().build()
        }

        // 3단계: JSON 파싱 및 도메인 변환 — 지원하지 않는 action이면 무시
        val dto = parsePayloadOrNull(body) ?: return ResponseEntity.badRequest().build()
        val event = dto.toDomain() ?: run {
            logger.debug("지원하지 않는 PR action 무시: {}", dto.action)
            return ResponseEntity.ok().build()
        }
        logger.info(
            "Webhook 수신: event={}, action={}, repo={}, pr={}",
            eventType, event.action, event.repositoryFullName, event.pullRequestNumber,
        )

        // 4단계: fire-and-forget — 즉시 202 반환 후 백그라운드 가상 스레드에서 AI 리뷰 처리
        applicationExecutor.submit {
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

    // Webhook 바디를 DTO로 역직렬화한다. 파싱 실패 시 WARN 로그 후 null 반환.
    private fun parsePayloadOrNull(body: String): WebhookPayloadDto? = try {
        objectMapper.readValue(body, WebhookPayloadDto::class.java)
    } catch (e: JacksonException) {
        logger.warn("Webhook payload 파싱 실패: {}", e.message)
        null
    }

}
