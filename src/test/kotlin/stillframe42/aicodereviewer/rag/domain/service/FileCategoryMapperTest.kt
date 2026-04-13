package stillframe42.aicodereviewer.rag.domain.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.API
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory.ARCH
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

    @Test
    fun `오탐 케이스 — auth·filter·rest 포함이지만 해당 카테고리 아닌 파일은 STYLE 또는 ARCH로 매핑된다`() {
        // AuthorService → Service로 끝나므로 ARCH (auth 오탐 방지)
        assertThat(FileCategoryMapper.selectCategory("AuthorService.kt")).isEqualTo(ARCH)
        // Restaurant → 어떤 패턴과도 suffix 매칭 안되므로 STYLE
        assertThat(FileCategoryMapper.selectCategory("Restaurant.kt")).isEqualTo(STYLE)
        // Interest → 어떤 패턴과도 suffix 매칭 안되므로 STYLE
        assertThat(FileCategoryMapper.selectCategory("Interest.kt")).isEqualTo(STYLE)
    }
}
