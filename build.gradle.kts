import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"
}

group = "stillframe42"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux") // ReactiveAdapterRegistry, ServerSentEvent, WebTestClient
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Data
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql") // Flyway 10+: PostgreSQL 지원 별도 모듈
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    // Spring AI
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")  // pgvector VectorStore
    implementation("org.springframework.ai:spring-ai-markdown-document-reader")

    // GitHub App JWT 인증
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2") // Spring MVC suspend 브릿지
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.projectreactor:reactor-test") // StepVerifier

    // Test — Testcontainers (버전은 Spring Boot BOM 관리)
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")

    // Test — WireMock (JDK 21 호환 standalone)
    testImplementation("org.wiremock:wiremock-standalone:3.10.0")

    // Test — Awaitility Kotlin DSL
    testImplementation("org.awaitility:awaitility-kotlin:4.2.2")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M2")
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// 모든 테스트 태스크 공통 설정 (JUnit 플랫폼 설정 제외)
tasks.withType<Test> {
    // 테스트 stdout(println) 콘솔에 출력
    testLogging {
        showStandardStreams = true
    }

    // byte-buddy(Mockito) 동적 에이전트 로딩 경고 억제 (JDK 21+)
    jvmArgs("-XX:+EnableDynamicAgentLoading")

    // ANTHROPIC_API_KEY 변경 시 Gradle 캐시 무효화 (up-to-date 방지)
    inputs.property("anthropicApiKey", System.getenv("ANTHROPIC_API_KEY") ?: "")
}

// 기본 테스트: experiment, quality-eval 태그 제외
tasks.test {
    useJUnitPlatform {
        excludeTags("experiment", "quality-eval")
    }
}

// 청킹 전략 실험 테스트 전용 태스크 — 실제 OpenAI API 키(sk-*) 필요
// 실행: ./gradlew experimentTest
tasks.register<Test>("experimentTest") {
    description = "청킹 전략 실험 테스트 실행 (실제 OpenAI API 사용)"
    group = "verification"

    // 커스텀 Test 태스크는 기본 test 태스크의 클래스패스를 명시적으로 지정해야 한다
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("experiment")
    }
}

// 컨벤션 검색 품질 평가 태스크 — 실제 OpenAI(임베딩) + Anthropic(평가) API 키 필요
// application-secret.yml의 API 키를 사용하므로 별도 인수 불필요
// 실행: ./gradlew qualityEvalTest
tasks.register<Test>("qualityEvalTest") {
    description = "컨벤션 검색 품질 평가 실행 (실제 OpenAI + Anthropic API 사용, 결과를 마크다운으로 저장)"
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("quality-eval")
    }
}

// 하이브리드 검색 품질 비교 실험 태스크 — 실제 OpenAI API 키(sk-*) 필요
// 실행: ./gradlew hybridExperimentTest
tasks.register<Test>("hybridExperimentTest") {
    description = "하이브리드 검색 품질 비교 실험 실행 (실제 OpenAI API 사용, 결과를 마크다운으로 저장)"
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("hybrid-experiment")
    }
}
