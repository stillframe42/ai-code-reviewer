package stillframe42.aicodereviewer.e2e.support

import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile

// Grafana Tempo 컨테이너 헬퍼 — 두 서비스의 OTLP span 을 수집해 종단 trace 검증에 사용한다.
// monitoring/tempo.yml 을 docker-compose.monitoring.yml 과 공유한다 — drift 시 두 곳을 동시에 갱신.
object TempoContainer {

    private val log = LoggerFactory.getLogger(TempoContainer::class.java)

    fun create(): GenericContainer<*> =
        GenericContainer("grafana/tempo:2.7.0")
            .withCommand("-config.file=/etc/tempo/tempo.yml")
            .withCopyFileToContainer(
                MountableFile.forHostPath("monitoring/tempo.yml"),
                "/etc/tempo/tempo.yml",
            )
            .withExposedPorts(3200, 4317, 4318)
            .waitingFor(Wait.forHttp("/ready").forPort(3200))
            .withLogConsumer(Slf4jLogConsumer(log).withPrefix("tempo"))
}
