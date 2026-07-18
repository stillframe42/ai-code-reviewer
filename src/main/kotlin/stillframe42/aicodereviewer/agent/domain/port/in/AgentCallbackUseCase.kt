package stillframe42.aicodereviewer.agent.domain.port.`in`

import stillframe42.aicodereviewer.agent.domain.model.AgentCallbackResult

interface AgentCallbackUseCase {
    fun handle(result: AgentCallbackResult)
}
