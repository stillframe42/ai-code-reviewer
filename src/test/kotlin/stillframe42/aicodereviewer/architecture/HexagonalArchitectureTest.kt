package stillframe42.aicodereviewer.architecture

import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

// 헥사고날 경계 룰 — Spring 컨텍스트 없이 컴파일된 main 클래스만 분석한다
// 정책: feature 간 결합은 상대 feature 의 domain(model·port·service·exception)만 허용
class HexagonalArchitectureTest {

    private val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages(BASE)

    @Test
    fun `feature 간 의존은 상대 feature 의 domain 패키지만 허용한다`() {
        FEATURES.forEach { feature ->
            val others = FEATURES.filter { it != feature }
            val otherFeaturePackages = others.map { "$BASE.$it.." }.toTypedArray()
            val otherFeatureDomainPackages = others.map { "$BASE.$it.domain.." }.toTypedArray()
            val forbidden = resideInAnyPackage(*otherFeaturePackages)
                .and(not(resideInAnyPackage(*otherFeatureDomainPackages)))

            noClasses().that().resideInAPackage("$BASE.$feature..")
                .should().dependOnClassesThat(forbidden)
                .because("feature 간 결합은 상대 feature 의 domain(model·port·service·exception)만 허용한다")
                .check(importedClasses)
        }
    }

    @Test
    fun `common 과 core 는 feature 패키지에 의존하지 않는다`() {
        noClasses().that().resideInAnyPackage("$BASE.common..", "$BASE.core..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(*FEATURES.map { "$BASE.$it.." }.toTypedArray())
            .because("공용 커널이 특정 feature 에 의존하면 의존 방향이 역전된다")
            .check(importedClasses)
    }

    @Test
    fun `domain model 과 port 는 외부 프레임워크에 의존하지 않는다`() {
        // domain/service 는 정책상 properties 의존 @Component 가 허용되므로 룰 대상에서 제외
        classes().that().resideInAnyPackage("..domain.model..", "..domain.port..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "kotlin..", "kotlinx..", "org.jetbrains.annotations..", "$BASE..")
            .because("domain 의 model·port 는 순수 Kotlin 으로 유지한다")
            .check(importedClasses)
    }

    @Test
    fun `application 은 adapter 에 의존하지 않는다`() {
        noClasses().that().resideInAPackage("$BASE..application..")
            .should().dependOnClassesThat().resideInAPackage("$BASE..adapter..")
            .because("application 은 포트 인터페이스를 통해서만 외부와 통신한다")
            .check(importedClasses)
    }

    companion object {
        private const val BASE = "stillframe42.aicodereviewer"
        private val FEATURES = listOf("agent", "chat", "evaluation", "github", "rag", "review")
    }
}
