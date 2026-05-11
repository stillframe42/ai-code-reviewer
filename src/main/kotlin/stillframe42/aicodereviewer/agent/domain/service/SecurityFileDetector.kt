package stillframe42.aicodereviewer.agent.domain.service

import stillframe42.aicodereviewer.github.domain.model.PrFile
import stillframe42.aicodereviewer.rag.domain.model.ConventionCategory
import stillframe42.aicodereviewer.rag.domain.service.FileCategoryMapper

object SecurityFileDetector {

    fun hasSecurityFile(files: List<PrFile>): Boolean =
        files.any { isSecurity(it) }

    fun firstSecurityFile(files: List<PrFile>): PrFile? =
        files.firstOrNull { isSecurity(it) }

    private fun isSecurity(file: PrFile): Boolean =
        FileCategoryMapper.selectCategory(file.filename) == ConventionCategory.SECURITY
}
