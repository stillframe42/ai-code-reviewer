package stillframe42.aicodereviewer.e2e.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2
import com.github.tomakehurst.wiremock.http.ResponseDefinition
import com.github.tomakehurst.wiremock.stubbing.ServeEvent

// src/test 의 동일 클래스를 source set 격리를 위해 복제. drift 발생 시 src/testFixtures 추출 검토.
class OpenAiEmbeddingBatchTransformer : ResponseDefinitionTransformerV2 {

    private val objectMapper = ObjectMapper()

    private val zeroVector = (1..1536).map { 0.1f }

    override fun getName(): String = "openai-embedding-batch"

    override fun applyGlobally(): Boolean = false

    override fun transform(serveEvent: ServeEvent): ResponseDefinition {
        val requestBody = serveEvent.request.bodyAsString
        val inputCount = runCatching {
            val tree = objectMapper.readTree(requestBody)
            val inputNode = tree.get("input")
            if (inputNode != null && inputNode.isArray) inputNode.size() else 1
        }.getOrDefault(1)

        val embeddingArray = (0 until inputCount).joinToString(",") { index ->
            val vectorStr = zeroVector.joinToString(",")
            """{"object":"embedding","embedding":[$vectorStr],"index":$index}"""
        }

        val responseJson = """{"object":"list","data":[$embeddingArray],"model":"text-embedding-3-small","usage":{"prompt_tokens":$inputCount,"total_tokens":$inputCount}}"""

        return ResponseDefinitionBuilder.responseDefinition()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(responseJson)
            .build()
    }
}
