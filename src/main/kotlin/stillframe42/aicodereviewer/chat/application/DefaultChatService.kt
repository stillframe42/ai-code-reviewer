package stillframe42.aicodereviewer.chat.application

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.springframework.stereotype.Service
import stillframe42.aicodereviewer.chat.domain.port.`in`.ChatUseCase
import stillframe42.aicodereviewer.chat.domain.port.out.AiChatPort
import stillframe42.aicodereviewer.core.AiProvider
import stillframe42.aicodereviewer.rag.domain.port.`in`.ConventionContextUseCase

// 채팅 유스케이스 구현 — AI 호출 전 RAG 컨벤션 컨텍스트를 조회해 전달한다
@Service
class DefaultChatService(
    private val aiChatPort: AiChatPort,
    private val conventionContextUseCase: ConventionContextUseCase,
) : ChatUseCase {

    override suspend fun chat(message: String, provider: AiProvider): String {
        val conventionContext = conventionContextUseCase.buildContext(query = message)
        return aiChatPort.chat(message, provider, conventionContext)
    }

    // flow { } 블록 내에서 suspend 함수(buildContext) 호출 가능
    override fun streamChat(message: String, provider: AiProvider): Flow<String> = flow {
        val conventionContext = conventionContextUseCase.buildContext(query = message)
        emitAll(aiChatPort.streamChat(message, provider, conventionContext))
    }
}
