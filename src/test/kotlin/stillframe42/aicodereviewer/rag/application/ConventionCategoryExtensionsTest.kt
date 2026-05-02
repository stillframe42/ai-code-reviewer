package stillframe42.aicodereviewer.rag.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory

class ConventionCategoryExtensionsTest {

    @Test
    fun `null 카테고리는 ALL 로 변환된다`() {
        val category: ConventionCategory? = null
        assertThat(category.nameOrAll()).isEqualTo("ALL")
    }

    @Test
    fun `non-null 카테고리는 enum name 으로 변환된다`() {
        assertThat(ConventionCategory.STYLE.nameOrAll()).isEqualTo("STYLE")
        assertThat(ConventionCategory.ARCH.nameOrAll()).isEqualTo("ARCH")
        assertThat(ConventionCategory.API.nameOrAll()).isEqualTo("API")
        assertThat(ConventionCategory.SECURITY.nameOrAll()).isEqualTo("SECURITY")
    }
}
