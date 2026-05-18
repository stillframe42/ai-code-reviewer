package stillframe42.aicodereviewer.e2e.support

import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Paths

// Remote 에이전트(ai-agent-service) 컨테이너 헬퍼.
// `../ai-agent-service/Dockerfile` 을 직접 빌드 — docker-compose.yml 의 build: ../ai-agent-service 와 동일 가정.
// drift 시 두 곳을 동시에 갱신.
object RemoteAgentContainer {

    private val log = LoggerFactory.getLogger(RemoteAgentContainer::class.java)

    fun create(wireMockHostPort: Int, logTail: ContainerLogTail? = null): GenericContainer<*> {
        val image = ImageFromDockerfile()
            .withFileFromPath(".", Paths.get("../ai-agent-service"))

        val container = GenericContainer(image)
            .withExposedPorts(8081)
            // Remote 에이전트의 OpenAI 호출이 호스트 WireMock 으로 루프백
            .withEnv("OPENAI_API_KEY", "test-key")
            .withEnv("OPENAI_BASE_URL", "http://host.docker.internal:$wireMockHostPort")
            // TODO: callback 흐름 활성 phase 진입 시 randomServerPort 를 동적 주입하는 헬퍼 도입.
            //       Phase 1 에서는 callback 미사용이므로 임시값 8080 사용.
            .withEnv("SPRING_BOOT_URL", "http://host.docker.internal:8080")
            .withEnv("GITHUB_TOKEN", "test-token")
            .withEnv("MAX_AGENT_STEPS", "10")
            .withExtraHost("host.docker.internal", "host-gateway")
            .waitingFor(Wait.forHttp("/health").forPort(8081))
            .withLogConsumer(Slf4jLogConsumer(log).withPrefix("remote-agent"))

        // logTail 이 있으면 추가 consumer 등록 — Slf4jLogConsumer 와 병렬 수집
        logTail?.let { container.withLogConsumer(it.consumer) }
        return container
    }
}
