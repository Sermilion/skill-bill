package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.application.runtime.RuntimeSingleton
import skillbill.engine.featuretask.review.core.FeatureTaskLastCommitReviewDriver
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeReviewDriver
import skillbill.infrastructure.host.JdkFeatureTaskRuntimeWorkerSupervisor
import skillbill.infrastructure.sqlite.SqliteFeatureTaskPhaseSettlementRepository
import skillbill.infrastructure.workflow.FileSystemCheckedOutBranchSource
import skillbill.infrastructure.workflow.FileSystemFeatureTaskRuntimeRunInvariantsSource
import skillbill.infrastructure.workflow.FileSystemFeatureTaskRuntimeSpecStatusWriter
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.featuretask.FeatureTaskPhaseSettlementRepository
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.ports.system.CheckedOutBranchSource
import skillbill.ports.taskruntime.FeatureTaskRuntimeRunInvariantsSource
import skillbill.ports.taskruntime.FeatureTaskRuntimeSpecStatusWriter
import skillbill.ports.taskruntime.FeatureTaskRuntimeWorkerSupervisor

internal interface RuntimeFeatureTaskProvides {
  @Provides @JvmSynthetic
  fun featureTaskRuntimeReviewDriver(launcher: GoalRunnerSubtaskLauncher): FeatureTaskRuntimeReviewDriver =
    FeatureTaskLastCommitReviewDriver(launcher)

  @Provides @JvmSynthetic
  fun featureTaskPhaseSettlementRepository(database: DatabaseSessionFactory): FeatureTaskPhaseSettlementRepository =
    SqliteFeatureTaskPhaseSettlementRepository(database)

  @Provides @JvmSynthetic
  fun featureTaskRuntimeRunInvariantsSource(
    adapter: FileSystemFeatureTaskRuntimeRunInvariantsSource,
  ): FeatureTaskRuntimeRunInvariantsSource = adapter

  @Provides @RuntimeSingleton @JvmSynthetic
  fun featureTaskRuntimeWorkerSupervisor(
    adapter: JdkFeatureTaskRuntimeWorkerSupervisor,
  ): FeatureTaskRuntimeWorkerSupervisor = adapter

  @Provides @JvmSynthetic
  fun featureTaskRuntimeSpecStatusWriter(
    adapter: FileSystemFeatureTaskRuntimeSpecStatusWriter,
  ): FeatureTaskRuntimeSpecStatusWriter = adapter

  @Provides @JvmSynthetic
  fun checkedOutBranchSource(source: FileSystemCheckedOutBranchSource): CheckedOutBranchSource = source
}
