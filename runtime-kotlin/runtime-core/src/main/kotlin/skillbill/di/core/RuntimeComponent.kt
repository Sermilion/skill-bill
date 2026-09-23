package skillbill.di.core
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import skillbill.application.agentrun.AgentRunService
import skillbill.application.config.ConfigResolutionService
import skillbill.application.install.ExternalAddonOverlayService
import skillbill.application.install.ExternalPlatformPackResolutionService
import skillbill.application.install.InstallService
import skillbill.application.learning.LearningService
import skillbill.application.review.parallel.runner.ParallelCodeReviewRunner
import skillbill.application.review.service.ReviewService
import skillbill.application.review.snapshot.ReviewSnapshotPruneService
import skillbill.application.runtime.RuntimeSingleton
import skillbill.application.scaffold.InstallAgentService
import skillbill.application.scaffold.SkillRemove
import skillbill.application.system.SystemService
import skillbill.application.telemetry.lifecycle.LifecycleTelemetryService
import skillbill.application.telemetry.service.TelemetryService
import skillbill.application.uninstall.SkillBillUninstallService
import skillbill.application.updatecheck.SkillBillUpdateService
import skillbill.application.updatecheck.UpdateCheckService
import skillbill.application.work.WorkListService
import skillbill.application.workflow.service.WorkflowService
import skillbill.di.experiment.RuntimeExperimentProvides
import skillbill.di.experiment.RuntimeExperimentTelemetryProvides
import skillbill.di.featurespec.RuntimeFeatureSpecProvides
import skillbill.di.featuretask.RuntimeFeatureTaskProvides
import skillbill.di.featuretask.RuntimeFeatureTaskValidatorProvides
import skillbill.di.goal.RuntimeGoalPlanningProvides
import skillbill.di.goal.RuntimeGoalPlanningSweepProvides
import skillbill.di.goal.RuntimeGoalRunnerLaunchProvides
import skillbill.di.goal.RuntimeGoalRunnerStoreProvides
import skillbill.di.install.RuntimeExternalPlatformPackProvides
import skillbill.di.install.RuntimeInstallPlanProvides
import skillbill.di.install.RuntimeInstallTargetProvides
import skillbill.di.install.RuntimeInstallerProvides
import skillbill.di.review.RuntimeReviewAddonCatalogProvides
import skillbill.di.review.RuntimeReviewEvidenceProvides
import skillbill.di.review.RuntimeReviewLaunchProvides
import skillbill.di.scaffold.RuntimeScaffoldProvides
import skillbill.di.scaffold.RuntimeScaffoldValidationProvides
import skillbill.di.telemetry.RuntimeTelemetryProvides
import skillbill.di.workflow.RuntimeWorkflowProvides
import skillbill.di.workflow.RuntimeWorkflowValidatorProvides
import skillbill.engine.featuretask.lifecycle.continuation.FeatureTaskContinuationLookupService
import skillbill.engine.featuretask.lifecycle.core.FeatureTaskRuntimeWorkerCoordinator
import skillbill.engine.featuretask.phase.core.FeatureTaskPhaseSettlementService
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runner.FeatureTaskRuntimeRunner
import skillbill.engine.featuretask.runner.FeatureTaskRuntimeStatusService
import skillbill.engine.goalplanning.GoalPlanningPreparationCheckpoint
import skillbill.engine.goalrunner.GoalOperatorDecisionService
import skillbill.engine.goalrunner.GoalRunner
import skillbill.engine.goalrunner.experiment.ExperimentGoalRunnerFactory
import skillbill.engine.goalrunner.experiment.ExperimentGoalRunnerPort
import skillbill.engine.goalrunner.findings.UnaddressedFindingsLedgerService
import skillbill.engine.goalrunner.planning.GoalPlanningLogService
import skillbill.engine.goalrunner.preflight.GoalPreflightService
import skillbill.engine.goalrunner.status.GoalRunnerStatusService
import skillbill.engine.work.IdeStatusService
import skillbill.infrastructure.host.concurrency.JvmInterruptSignalPort
import skillbill.model.EnvironmentContext
import skillbill.model.OptionalCallbacks
import skillbill.model.RuntimeContext
import skillbill.model.TransportContext
import skillbill.model.WorkflowOpsContext
import skillbill.ports.agentaddon.ExternalAgentAddonSourceConfigPort
import skillbill.ports.concurrency.InterruptSignalPort
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.featurespec.FeatureSpecPathResolverPort
import skillbill.ports.install.baseline.InstalledWorkspaceBaselineStatusPort
import skillbill.ports.install.mcp.InstallMcpRegistrationPort
import skillbill.ports.install.nativeagent.InstallNativeAgentLinkPort
import skillbill.ports.install.selection.InstallSelectionPersistencePort
import skillbill.ports.repository.RepositoryEnclosingRootPort
import skillbill.ports.scaffold.ScaffoldCatalogGateway
import skillbill.ports.scaffold.ScaffoldGateway
import skillbill.ports.scaffold.UnsupportedScaffoldGateway
import skillbill.ports.system.UninstallPathsPort
import skillbill.ports.taskruntime.FeatureTaskRuntimeRunInvariantsSource
import skillbill.ports.telemetry.transport.RemoteTransportPort
import skillbill.ports.telemetry.transport.TelemetryConfigStore
import skillbill.ports.telemetry.transport.TelemetryLevelMutator
import skillbill.ports.validation.RepoValidationGateway
import java.time.Clock

@RuntimeSingleton
@Component
abstract class RuntimeComponent(
  private val inputRuntimeContext: RuntimeContext,
) :
  RuntimeInstallTargetProvides,
    RuntimeInstallPlanProvides,
    RuntimeExternalPlatformPackProvides,
    RuntimeTelemetryProvides,
    RuntimeGoalPlanningProvides,
    RuntimeGoalPlanningSweepProvides,
    RuntimeGoalRunnerStoreProvides,
    RuntimeGoalRunnerLaunchProvides,
    RuntimeReviewLaunchProvides,
    RuntimeReviewAddonCatalogProvides,
    RuntimeReviewEvidenceProvides,
    RuntimeFeatureTaskProvides,
    RuntimeFeatureSpecProvides,
    RuntimeWorkflowProvides,
    RuntimeWorkflowValidatorProvides,
    RuntimeFeatureTaskValidatorProvides,
    RuntimeScaffoldProvides,
    RuntimeScaffoldValidationProvides,
    RuntimeInstallerProvides,
    RuntimeExperimentProvides,
    RuntimeExperimentTelemetryProvides,
    RuntimeDiagnosticsProvides {
  private val resolvedRuntimeContext: RuntimeContext by lazy {
    RuntimeBootstrapBindings.runtimeContext(inputRuntimeContext)
  }

  @Provides @JvmSynthetic
  fun runtimeContext(): RuntimeContext = resolvedRuntimeContext

  @Provides @JvmSynthetic
  fun experimentGoalRunnerFactory(): ExperimentGoalRunnerFactory =
    ExperimentGoalRunnerFactory { context ->
      val component = RuntimeComponent::class.create(context)
      ExperimentGoalRunnerPort { request -> component.goalRunner.run(request) }
    }

  @Provides @JvmSynthetic
  fun environmentContext(ctx: RuntimeContext): EnvironmentContext = ctx.environment

  @Provides @JvmSynthetic
  fun transportContext(ctx: RuntimeContext): TransportContext = ctx.transport

  @Provides @JvmSynthetic
  fun remoteTransportPort(ctx: TransportContext): RemoteTransportPort =
    RuntimeBootstrapBindings.remoteTransportPort(ctx)

  @Provides @JvmSynthetic
  fun workflowOpsContext(ctx: RuntimeContext): WorkflowOpsContext = ctx.workflowOps

  @Provides @JvmSynthetic
  fun optionalCallbacks(ctx: RuntimeContext): OptionalCallbacks = ctx.callbacks

  @Provides @JvmSynthetic
  fun repositoryEnclosingRootPort(): RepositoryEnclosingRootPort =
    RuntimeBootstrapBindings.repositoryEnclosingRootPort()

  @Provides @RuntimeSingleton @JvmSynthetic
  fun databaseSessionFactory(
    context: EnvironmentContext,
    clock: Clock,
    diagnostics: RuntimeDiagnostics,
  ): DatabaseSessionFactory = RuntimeBootstrapBindings.databaseSessionFactory(context, clock, diagnostics)

  @Provides @JvmSynthetic
  fun interruptSignal(): InterruptSignalPort = JvmInterruptSignalPort

  abstract val resolvedEnvironmentContext: EnvironmentContext

  abstract val repositoryEnclosingRootPort: RepositoryEnclosingRootPort

  abstract val featureTaskContinuationLookupService: FeatureTaskContinuationLookupService
  abstract val featureTaskPhaseSettlementService: FeatureTaskPhaseSettlementService
  abstract val unaddressedFindingsLedgerService: UnaddressedFindingsLedgerService

  abstract val parallelCodeReviewRunner: ParallelCodeReviewRunner
  abstract val configResolutionService: ConfigResolutionService
  abstract val externalAgentAddonSourceConfigPort: ExternalAgentAddonSourceConfigPort
  abstract val installService: InstallService
  abstract val externalAddonOverlayService: ExternalAddonOverlayService
  abstract val externalPlatformPackResolutionService: ExternalPlatformPackResolutionService
  abstract val agentRunService: AgentRunService
  abstract val featureTaskRuntimePhaseRecorder: FeatureTaskRuntimePhaseRecorder
  abstract val featureTaskRuntimeRunner: FeatureTaskRuntimeRunner
  abstract val featureTaskRuntimeStatusService: FeatureTaskRuntimeStatusService
  abstract val featureTaskRuntimeWorkerCoordinator: FeatureTaskRuntimeWorkerCoordinator
  abstract val goalPlanningPreparationCheckpoint: GoalPlanningPreparationCheckpoint
  abstract val featureTaskRuntimeRunInvariantsSource: FeatureTaskRuntimeRunInvariantsSource
  abstract val featureSpecPathResolverPort: FeatureSpecPathResolverPort
  abstract val goalRunner: GoalRunner
  abstract val goalPreflightService: GoalPreflightService
  abstract val goalRunnerStatusService: GoalRunnerStatusService
  abstract val goalPlanningLogService: GoalPlanningLogService
  abstract val goalOperatorDecisionService: GoalOperatorDecisionService
  abstract val installAgentService: InstallAgentService
  abstract val installMcpRegistrationPort: InstallMcpRegistrationPort
  abstract val installNativeAgentLinkPort: InstallNativeAgentLinkPort
  abstract val installSelectionPersistencePort: InstallSelectionPersistencePort
  abstract val installedWorkspaceBaselineStatusPort: InstalledWorkspaceBaselineStatusPort
  abstract val learningService: LearningService
  abstract val lifecycleTelemetryService: LifecycleTelemetryService
  abstract val repoValidationGateway: RepoValidationGateway
  abstract val reviewService: ReviewService
  abstract val reviewSnapshotPruneService: ReviewSnapshotPruneService
  abstract val runtimeDiagnostics: RuntimeDiagnostics
  abstract val scaffoldCatalogGateway: ScaffoldCatalogGateway
  abstract val scaffoldGateway: ScaffoldGateway
  abstract val skillRemove: SkillRemove
  abstract val systemService: SystemService
  abstract val skillBillUpdateService: SkillBillUpdateService
  abstract val updateCheckService: UpdateCheckService
  abstract val skillBillUninstallService: SkillBillUninstallService
  abstract val telemetryConfigStorePort: TelemetryConfigStore
  abstract val telemetryLevelMutator: TelemetryLevelMutator
  abstract val telemetryService: TelemetryService
  abstract val uninstallPathsPort: UninstallPathsPort
  abstract val unsupportedScaffoldGateway: UnsupportedScaffoldGateway
  abstract val workflowService: WorkflowService
  abstract val workListService: WorkListService
  abstract val ideStatusService: IdeStatusService
}
