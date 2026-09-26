package skillbill.engine.featuretask.slot

import skillbill.engine.featuretask.slot.codereview.InlineReviewStrategy
import skillbill.engine.featuretask.slot.runner.DefaultPhaseRunner
import skillbill.engine.featuretask.slot.strategy.AcceptanceAuditStrategy
import skillbill.engine.featuretask.slot.strategy.AgentPlanStrategy
import skillbill.engine.featuretask.slot.strategy.AgentPreplanStrategy
import skillbill.engine.featuretask.slot.strategy.BoundaryHistoryStrategy
import skillbill.engine.featuretask.slot.strategy.ImplementThenSimplifyStrategy
import skillbill.engine.featuretask.slot.strategy.PrDescriptionStrategy
import skillbill.engine.featuretask.slot.strategy.RoutedQualityGateStrategy
import skillbill.engine.featuretask.slot.strategy.RuntimeCommitStrategy
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.ports.workflow.gitops.NoopWorkflowGitOperations
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.core.PhaseSlot

fun statusProjectionPhaseStrategies(): PhaseStrategyLookup =
  testPhaseStrategies(
    GoalRunnerSubtaskLauncher { error("Status projection must not launch a phase.") },
    NoopWorkflowGitOperations,
    ApprovingReviewPhaseRunner,
  )

fun testPhaseStrategies(
  launcher: GoalRunnerSubtaskLauncher,
  gitOperations: WorkflowGitOperations,
  reviewRunner: PhaseRunner? = null,
): PhaseStrategyLookup {
  val runner = { DefaultPhaseRunner(launcher, gitOperations) }
  val codeReviewRunner = reviewRunner?.let { reviewRoutingPhaseRunner(it, runner()) } ?: runner()
  val strategies =
    listOf(
      AgentPreplanStrategy(runner()),
      AgentPlanStrategy(runner()),
      ImplementThenSimplifyStrategy(runner()),
      AcceptanceAuditStrategy(runner()),
      InlineReviewStrategy(codeReviewRunner),
      RoutedQualityGateStrategy(runner()),
      BoundaryHistoryStrategy(runner()),
      RuntimeCommitStrategy(runner()),
      PrDescriptionStrategy(runner()),
    )
  val registry = PhaseStrategyRegistry(strategies)
  val bindings: Map<PhaseSlot, PhaseStrategyBinding> =
    strategies.associate { it.slot to PhaseStrategyBinding.Fixed(it.strategyId) } +
      mapOf(
        PhaseSlot.CODE_REVIEW to
          PhaseStrategyBinding.ByCodeReviewMode(
            CodeReviewExecutionMode.entries.associateWith { InlineReviewStrategy.ID },
          ),
        PhaseSlot.QUALITY_GATE to
          PhaseStrategyBinding.ByQualityGate(
            FeatureTaskRuntimeQualityGateSelection.entries.associateWith { RoutedQualityGateStrategy.ID },
          ),
      )
  return PhaseStrategyLookup(registry, PhaseStrategySelection(registry, bindings))
}
