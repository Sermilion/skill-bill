package skillbill.engine.goalrunner

import skillbill.engine.recovery.recommendedDurableChildRecoveryCommand as recoveryRecommendedDurableChildRecoveryCommand
import skillbill.engine.recovery.scopedChildRecoveryCommand as recoveryScopedChildRecoveryCommand
import skillbill.engine.recovery.staleChildPlanningRecoveryCommand as recoveryStaleChildPlanningRecoveryCommand

internal typealias DurableChildRecoveryClass = skillbill.engine.recovery.DurableChildRecoveryClass

fun scopedChildRecoveryCommand(issueKey: String, subtaskId: Int): String =
  recoveryScopedChildRecoveryCommand(issueKey, subtaskId)

fun recommendedDurableChildRecoveryCommand(
  issueKey: String,
  subtaskId: Int,
  subtaskStatus: skillbill.workflow.model.DecompositionStatus?,
  childProgress: skillbill.ports.goalrunner.runner.model.GoalRunnerWorkflowProgress?,
): String = recoveryRecommendedDurableChildRecoveryCommand(issueKey, subtaskId, subtaskStatus, childProgress)

fun staleChildPlanningRecoveryCommand(issueKey: String, subtaskId: Int): String =
  recoveryStaleChildPlanningRecoveryCommand(issueKey, subtaskId)

internal fun classifyDurableChild(
  progress: skillbill.ports.goalrunner.runner.model.GoalRunnerWorkflowProgress?,
): DurableChildRecoveryClass = skillbill.engine.recovery.classifyDurableChild(progress)
