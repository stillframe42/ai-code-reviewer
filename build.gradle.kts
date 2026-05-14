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

sourceSets {
    val evaluationTest by creating {
        compileClasspath += sourceSets["test"].output + sourceSets["main"].output
        runtimeClasspath += sourceSets["test"].output + sourceSets["main"].output
    }
}

val evaluationTestImplementation by configurations.getting {
    extendsFrom(configurations["testImplementation"])
}
val evaluationTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations["testRuntimeOnly"])
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

    // 통합 테스트 다중 propertySource 로 별도 Spring 컨텍스트가 누적될 때 default heap (512MB) 부족
    // pgvector 의 BatchingStrategy bean 생성에서 OOM 발생을 방지
    maxHeapSize = "4g"

    // byte-buddy(Mockito) 동적 에이전트 로딩 경고 억제 (JDK 21+)
    jvmArgs("-XX:+EnableDynamicAgentLoading")

    // ANTHROPIC_API_KEY 변경 시 Gradle 캐시 무효화 (up-to-date 방지)
    inputs.property("anthropicApiKey", System.getenv("ANTHROPIC_API_KEY") ?: "")
}

// 기본 테스트: experiment, quality-eval, hybrid-experiment 태그 제외
tasks.test {
    useJUnitPlatform {
        excludeTags("experiment", "quality-eval", "hybrid-experiment")
    }
}

// 커스텀 Test 태스크는 기본 test 태스크의 클래스패스를 명시적으로 지정해야 한다
fun registerExperimentTask(name: String, tag: String, description: String) {
    tasks.register<Test>(name) {
        this.description = description
        group = "verification"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform { includeTags(tag) }
    }
}

tasks.register<Test>("evaluationTest") {
    description = "평가/벤치마크/실험성 IT 실행 (수동/주기 워크플로 전용 — 실제 API 키 필요)"
    group = "verification"
    testClassesDirs = sourceSets["evaluationTest"].output.classesDirs
    classpath = sourceSets["evaluationTest"].runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter("test")
}

// 실행: ./gradlew experimentTest      — 실제 OpenAI API 키(sk-*) 필요
// 실행: ./gradlew qualityEvalTest     — 실제 OpenAI(임베딩) + Anthropic(평가) API 키 필요
// 실행: ./gradlew hybridExperimentTest — 실제 OpenAI API 키(sk-*) 필요
registerExperimentTask("experimentTest",       "experiment",        "청킹 전략 실험 테스트 실행 (실제 OpenAI API 사용)")
registerExperimentTask("qualityEvalTest",      "quality-eval",      "컨벤션 검색 품질 평가 실행 (실제 OpenAI + Anthropic API 사용, 결과를 마크다운으로 저장)")
registerExperimentTask("hybridExperimentTest", "hybrid-experiment", "하이브리드 검색 품질 비교 실험 실행 (실제 OpenAI API 사용, 결과를 마크다운으로 저장)")
