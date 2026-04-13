package stillframe42.aicodereviewer.rag.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.ARCH
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.API
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.SECURITY
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.STYLE

class FileCategoryMapperTest {

    @Test
    fun `Security 포함 파일명은 SECURITY로 매핑된다`() {
        assertThat(FileCategoryMapper.selectCategory("SecurityConfig.kt")).isEqualTo(SECURITY)
        assertThat(FileCategoryMapper.selectCategory("JwtAuthFilter.kt")).isEqualTo(SECURITY)
        assertThat(FileCategoryMapper.selectCategory("OAuthInterceptor.kt")).isEqualTo(SECURITY)
    }

    @Test
    fun `Controller 포함 파일명은 API로 매핑된다`() {
        assertThat(FileCategoryMapper.selectCategory("OrderController.kt")).isEqualTo(API)
        assertThat(FileCategoryMapper.selectCategory("ReviewHandler.kt")).isEqualTo(API)
    }

    @Test
    fun `Service·UseCase·Repository·Config 포함 파일명은 ARCH로 매핑된다`() {
        assertThat(FileCategoryMapper.selectCategory("OrderService.kt")).isEqualTo(ARCH)
        assertThat(FileCategoryMapper.selectCategory("ConventionIndexUseCase.kt")).isEqualTo(ARCH)
        assertThat(FileCategoryMapper.selectCategory("UserRepository.kt")).isEqualTo(ARCH)
        assertThat(FileCategoryMapper.selectCategory("RagProperties.kt")).isEqualTo(ARCH)
    }

    @Test
    fun `패턴 미매칭 파일명은 STYLE로 매핑된다`() {
        assertThat(FileCategoryMapper.selectCategory("Order.kt")).isEqualTo(STYLE)
        assertThat(FileCategoryMapper.selectCategory("OrderStatus.kt")).isEqualTo(STYLE)
        assertThat(FileCategoryMapper.selectCategory("StringUtils.kt")).isEqualTo(STYLE)
    }

    @Test
    fun `전체 경로가 입력되어도 파일명 기준으로 매핑된다`() {
        assertThat(
            FileCategoryMapper.selectCategory("src/main/kotlin/stillframe42/SecurityConfig.kt")
        ).isEqualTo(SECURITY)
        assertThat(
            FileCategoryMapper.selectCategory("src/main/kotlin/stillframe42/Order.kt")
        ).isEqualTo(STYLE)
    }

    @Test
    fun `SECURITY 패턴이 Config보다 우선한다`() {
        // SecurityConfig → Security 패턴이 먼저 매핑
        assertThat(FileCategoryMapper.selectCategory("SecurityConfig.kt")).isEqualTo(SECURITY)
    }
}
