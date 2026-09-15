package dev.skillbill.intellij.application

import dev.skillbill.intellij.domain.SkillBillStatusOutcome
import java.nio.file.Path


fun interface StatusRepository {
    suspend fun fetchStatus(projectRoot: Path): SkillBillStatusOutcome
}
