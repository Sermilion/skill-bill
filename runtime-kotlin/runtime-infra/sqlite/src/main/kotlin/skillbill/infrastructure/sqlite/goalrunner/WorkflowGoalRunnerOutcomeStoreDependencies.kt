package skillbill.infrastructure.sqlite.goalrunner

import me.tatarka.inject.annotations.Inject
import skillbill.ports.decomposition.DecompositionManifestProjectionWriter
import skillbill.ports.goalrunner.persistence.GoalRunnerChildRepairRunnerPort
import skillbill.ports.taskruntime.FeatureTaskRuntimeWorkerSupervisor
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.engine.WorkflowSnapshotValidator
import skillbill.workflow.goal.GoalObservabilityEventValidator
import skillbill.workflow.goal.GoalProgressEventValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import java.time.Clock

class WorkflowGoalRunnerOutcomeStoreDependencies @Inject constructor(
  val workflowSnapshotValidator: WorkflowSnapshotValidator,
  val goalObservabilityEventValidator: GoalObservabilityEventValidator,
  val goalProgressEventValidator: GoalProgressEventValidator,
  val gitOperations: WorkflowGitOperations,
  val phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
  val workerSupervisor: FeatureTaskRuntimeWorkerSupervisor,
  val clock: Clock,
  val decompositionManifestValidator: DecompositionManifestValidator,
  val decompositionManifestStore: DecompositionManifestStore,
  val decompositionManifestWriter: DecompositionManifestProjectionWriter,
  val childRepairExecutor: GoalRunnerChildRepairRunnerPort,
)
