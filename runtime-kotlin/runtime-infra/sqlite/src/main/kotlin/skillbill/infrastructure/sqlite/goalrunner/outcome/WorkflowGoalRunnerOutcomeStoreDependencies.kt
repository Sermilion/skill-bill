package skillbill.infrastructure.sqlite.goalrunner.outcome
import me.tatarka.inject.annotations.Inject
import skillbill.ports.decomposition.DecompositionManifestProjectionWriter
import skillbill.ports.goalrunner.persistence.GoalRunnerChildRepairRunnerPort
import skillbill.ports.taskruntime.FeatureTaskRuntimeWorkerSupervisor
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.decomposition.DecompositionManifestValidator
import skillbill.ports.workflow.WorkflowSnapshotValidator
import skillbill.ports.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import skillbill.ports.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import java.time.Clock

class WorkflowGoalRunnerOutcomeStoreDependencies
  @Inject
  constructor(
    val workflowSnapshotValidator: WorkflowSnapshotValidator,
    val goalObservabilityEventValidator: FeatureTaskRuntimeWireArtifactValidator,
    val goalProgressEventValidator: FeatureTaskRuntimeWireArtifactValidator,
    val gitOperations: WorkflowGitOperations,
    val phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
    val workerSupervisor: FeatureTaskRuntimeWorkerSupervisor,
    val clock: Clock,
    val decompositionManifestValidator: DecompositionManifestValidator,
    val decompositionManifestStore: DecompositionManifestStore,
    val decompositionManifestWriter: DecompositionManifestProjectionWriter,
    val childRepairExecutor: GoalRunnerChildRepairRunnerPort,
  )
