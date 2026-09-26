package skillbill.di.featuretask

import me.tatarka.inject.annotations.Provides
import skillbill.engine.featuretask.review.core.FeatureTaskLastCommitReviewDriver
import skillbill.engine.featuretask.slot.PhaseRunner
import skillbill.engine.featuretask.slot.PhaseStrategyBinding
import skillbill.engine.featuretask.slot.PhaseStrategyLookup
import skillbill.engine.featuretask.slot.PhaseStrategyRegistry
import skillbill.engine.featuretask.slot.PhaseStrategySelection
import skillbill.engine.featuretask.slot.runner.DefaultPhaseRunner
import skillbill.engine.featuretask.slot.strategy.AcceptanceAuditStrategy
import skillbill.engine.featuretask.slot.strategy.AgentPlanStrategy
import skillbill.engine.featuretask.slot.strategy.AgentPreplanStrategy
import skillbill.engine.featuretask.slot.strategy.BoundaryHistoryStrategy
import skillbill.engine.featuretask.slot.strategy.ImplementThenSimplifyStrategy
import skillbill.engine.featuretask.slot.strategy.InlineCodeReviewStrategy
import skillbill.engine.featuretask.slot.strategy.PrDescriptionStrategy
import skillbill.engine.featuretask.slot.strategy.RoutedQualityGateStrategy
import skillbill.engine.featuretask.slot.strategy.RuntimeCommitStrategy
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.core.PhaseSlot

internal interface RuntimeFeatureTaskSlotProvides {
  @Provides
  fun phaseRunner(
    launcher: GoalRunnerSubtaskLauncher,
    gitOperations: WorkflowGitOperations,
  ): PhaseRunner = DefaultPhaseRunner(launcher, gitOperations)

  @Provides
  fun phaseStrategyRegistry(runner: () -> PhaseRunner): PhaseStrategyRegistry =
    PhaseStrategyRegistry(
      listOf(
        AgentPreplanStrategy(runner()),
        AgentPlanStrategy(runner()),
        ImplementThenSimplifyStrategy(runner()),
        AcceptanceAuditStrategy(runner()),
        InlineCodeReviewStrategy(runner(), ::FeatureTaskLastCommitReviewDriver),
        RoutedQualityGateStrategy(runner()),
        BoundaryHistoryStrategy(runner()),
        RuntimeCommitStrategy(runner()),
        PrDescriptionStrategy(runner()),
      ),
    )

  @Provides
  fun phaseStrategySelection(registry: PhaseStrategyRegistry): PhaseStrategySelection =
    PhaseStrategySelection(
      registry,
      mapOf(
        PhaseSlot.PREPLAN to PhaseStrategyBinding.Fixed(AgentPreplanStrategy.ID),
        PhaseSlot.PLAN to PhaseStrategyBinding.Fixed(AgentPlanStrategy.ID),
        PhaseSlot.IMPLEMENTATION to PhaseStrategyBinding.Fixed(ImplementThenSimplifyStrategy.ID),
        PhaseSlot.AUDIT to PhaseStrategyBinding.Fixed(AcceptanceAuditStrategy.ID),
        PhaseSlot.CODE_REVIEW to
          PhaseStrategyBinding.ByCodeReviewMode(
            CodeReviewExecutionMode.entries.associateWith { InlineCodeReviewStrategy.ID },
          ),
        PhaseSlot.QUALITY_GATE to
          PhaseStrategyBinding.ByQualityGate(
            FeatureTaskRuntimeQualityGateSelection.entries.associateWith { RoutedQualityGateStrategy.ID },
          ),
        PhaseSlot.WRITE_HISTORY to PhaseStrategyBinding.Fixed(BoundaryHistoryStrategy.ID),
        PhaseSlot.COMMIT_PUSH to PhaseStrategyBinding.Fixed(RuntimeCommitStrategy.ID),
        PhaseSlot.PULL_REQUEST to PhaseStrategyBinding.Fixed(PrDescriptionStrategy.ID),
      ),
    )

  @Provides
  fun phaseStrategyLookup(
    registry: PhaseStrategyRegistry,
    selection: PhaseStrategySelection,
  ): PhaseStrategyLookup = PhaseStrategyLookup(registry, selection)
}
