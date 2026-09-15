package dev.skillbill.intellij.composition

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import dev.skillbill.intellij.application.GoalMutationRepository
import dev.skillbill.intellij.application.StatusRefreshCoordinator
import dev.skillbill.intellij.infrastructure.prefs.IntelliJPreferenceCache
import dev.skillbill.intellij.presentation.SkillBillStatusViewModel
import java.nio.file.Path


class SkillBillProjectStatusService(
    private val project: Project,
) : Disposable {
    private val root: SkillBillStatusCompositionRoot =
        SkillBillStatusCompositionRoot.create(
            projectRoot = Path.of(project.basePath ?: "."),
            preferences = IntelliJPreferenceCache.forProject(project),
            parentDisposable = this,
        )

    val viewModel: SkillBillStatusViewModel
        get() = root.viewModel

    val coordinator: StatusRefreshCoordinator
        get() = root.coordinator

    val goalPauseRepository: GoalMutationRepository
        get() = root.goalPauseRepository

    val goalStopRepository: GoalMutationRepository
        get() = root.goalStopRepository

    init {
        Disposer.register(project, this)
    }

    override fun dispose() {
        root.dispose()
    }
}
