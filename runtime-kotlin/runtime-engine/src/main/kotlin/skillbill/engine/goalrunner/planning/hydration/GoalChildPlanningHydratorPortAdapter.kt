package skillbill.engine.goalrunner.planning.hydration

import me.tatarka.inject.annotations.Inject
import skillbill.engine.goalrunner.planning.model.GoalChildPlanningHydration
import skillbill.ports.goalrunner.GoalRunnerPersistenceSession
import skillbill.ports.goalrunner.persistence.GoalChildPlanningHydratorPort
import skillbill.ports.goalrunner.persistence.model.GoalChildPlanningHydrationResult
import skillbill.ports.goalrunner.runner.model.GoalChildPlanningHydrationRequest
import skillbill.ports.goalrunner.runner.model.GoalRunnerChildWorkflowSetup
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseOutputValidator
import java.time.Clock

@Inject
class GoalChildPlanningHydratorPortAdapter(
  phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
  planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator,
  clock: Clock,
) : GoalChildPlanningHydratorPort {
  private val hydrator = GoalChildPlanningHydrator(phaseOutputValidator, planningProjectionValidator, clock)

  override fun hydrate(
    unitOfWork: GoalRunnerPersistenceSession,
    setup: GoalRunnerChildWorkflowSetup,
    request: GoalChildPlanningHydrationRequest,
  ): GoalChildPlanningHydrationResult = hydrator.hydrate(unitOfWork, setup, request).toPortResult()

  override fun requireMatchingImport(
    unitOfWork: GoalRunnerPersistenceSession,
    existing: WorkflowStateSnapshot,
    setup: GoalRunnerChildWorkflowSetup,
  ) = hydrator.requireMatchingImport(unitOfWork, existing, setup)

  private fun GoalChildPlanningHydration.toPortResult() =
    GoalChildPlanningHydrationResult(
      currentStepId = currentStepId,
      stepUpdates = stepUpdates,
      artifacts = artifacts,
    )
}
