package skillbill.engine.featuretask.slot

import skillbill.engine.featuretask.slot.codereview.InlineReviewStrategy
import skillbill.engine.featuretask.slot.qualitygate.agentvalidate.AgentValidateStrategy
import skillbill.engine.featuretask.slot.qualitygate.packbuild.PackBuildStrategy
import skillbill.engine.featuretask.slot.strategy.AcceptanceAuditStrategy
import skillbill.engine.featuretask.slot.strategy.AgentPlanStrategy
import skillbill.engine.featuretask.slot.strategy.AgentPreplanStrategy
import skillbill.engine.featuretask.slot.strategy.BoundaryHistoryStrategy
import skillbill.engine.featuretask.slot.strategy.ImplementThenSimplifyStrategy
import skillbill.engine.featuretask.slot.strategy.PrDescriptionStrategy
import skillbill.engine.featuretask.slot.strategy.RuntimeCommitStrategy
import skillbill.workflow.taskruntime.model.core.PhaseStepPolicy
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT_FIX
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PR
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PREPLAN
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_SIMPLIFY
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_WRITE_HISTORY
import kotlin.test.Test
import kotlin.test.assertEquals

class PhaseStrategyCompositionTest {
  private val runner =
    object : PhaseRunner {
      override fun run(
        input: PhaseStepInput,
        state: PhaseRunState,
      ): PhaseStepOutput = error("Policy lookups must not launch a step.")
    }

  private val strategies =
    listOf(
      AgentPreplanStrategy(runner),
      AgentPlanStrategy(runner),
      ImplementThenSimplifyStrategy(runner),
      AcceptanceAuditStrategy(runner),
      InlineReviewStrategy(runner),
      PackBuildStrategy(runner),
      AgentValidateStrategy(runner),
      BoundaryHistoryStrategy(runner),
      RuntimeCommitStrategy(runner),
      PrDescriptionStrategy(runner),
    )

  @Test
  fun `strategies declare the pre-change step policy table`() {
    val actual = strategies.flatMap { strategy -> strategy.steps.map { it to strategy.policyFor(it) } }.toMap()

    assertEquals(EXPECTED_POLICIES, actual)
  }

  private companion object {
    val EXPECTED_POLICIES =
      mapOf(
        PHASE_PREPLAN to policy(relaunch = true),
        PHASE_PLAN to policy(relaunch = true),
        PHASE_IMPLEMENT to policy(mutating = true, relaunch = true, fileMutating = true),
        PHASE_SIMPLIFY to policy(mutating = true, relaunch = true, single = true, fileMutating = true),
        PHASE_AUDIT to policy(single = true, fileMutating = true),
        PHASE_REVIEW to policy(relaunch = true, fileMutating = true, generationScoped = true),
        PHASE_VERIFY_FINDINGS to policy(relaunch = true, readOnlyIdle = true, fileMutating = true),
        PHASE_IMPLEMENT_FIX to policy(mutating = true, relaunch = true, fileMutating = true, generationScoped = true),
        PHASE_BUILD to policy(relaunch = true, fileMutating = true),
        PHASE_VALIDATE to policy(relaunch = true, fileMutating = true),
        PHASE_WRITE_HISTORY to policy(fileMutating = true),
        PHASE_COMMIT_PUSH to policy(fileMutating = true),
        PHASE_PR to policy(fileMutating = true),
      )

    fun policy(
      mutating: Boolean = false,
      relaunch: Boolean = false,
      single: Boolean = false,
      readOnlyIdle: Boolean = false,
      fileMutating: Boolean = false,
      generationScoped: Boolean = false,
    ) = PhaseStepPolicy(mutating, relaunch, single, readOnlyIdle, fileMutating, generationScoped)
  }
}
