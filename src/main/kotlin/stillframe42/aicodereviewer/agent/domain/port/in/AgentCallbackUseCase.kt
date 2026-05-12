package stillframe42.aicodereviewer.agent.domain.port.`in`

import stillframe42.aicodereviewer.agent.adapter.`in`.web.dto.AgentCallbackResult

interface AgentCallbackUseCase {
    suspend fun handle(result: AgentCallbackResult)
}
