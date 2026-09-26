package skillbill.engine.featuretask.slot.strategy

import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimeCurrentPhaseExecutionContext
import skillbill.engine.featuretask.phase.core.attemptPhaseExecution
import skillbill.engine.featuretask.phase.core.defaultPhaseExecution
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeReviewDriver
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.core.PhaseOutcome
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.runloop.phase.FeatureTaskRuntimeRunLoopPhaseRunner
import skillbill.engine.featuretask.slot.PhaseRunState
import skillbill.engine.featuretask.slot.PhaseRunner
import skillbill.engine.featuretask.slot.PhaseStrategyStatusProjection
import skillbill.engine.work.model.IdeStatusCurrentPhaseExecution
import skillbill.engine.work.model.IdeStatusCurrentPhaseExecutionKind
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.model.core.PhaseSlot
import skillbill.workflow.taskruntime.model.core.PhaseStepPolicy
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

class InlineCodeReviewStrategy(
  override val runner: PhaseRunner,
  private val reviewDriver: (PhaseRunner, PhaseRunState) -> FeatureTaskRuntimeReviewDriver,
) : PhaseStrategyStatusProjection() {
  private val policies =
    mapOf(
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW to
        PhaseStepPolicy(
          mutating = false,
          relaunchOnInvalidOutput = true,
          singleAgentSession = false,
          readOnlyIdle = true,
          fileMutating = true,
          generationScoped = true,
        ),
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS to
        PhaseStepPolicy(
          mutating = false,
          relaunchOnInvalidOutput = true,
          singleAgentSession = false,
          readOnlyIdle = true,
          fileMutating = true,
          generationScoped = false,
        ),
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT_FIX to
        PhaseStepPolicy(
          mutating = true,
          relaunchOnInvalidOutput = true,
          singleAgentSession = false,
          readOnlyIdle = false,
          fileMutating = true,
          generationScoped = true,
        ),
    )

  override val slot: PhaseSlot = PhaseSlot.CODE_REVIEW
  override val strategyId: String = ID
  override val steps: List<String> = policies.keys.toList()
  override val entryStep: String = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW

  override fun policyFor(stepId: String): PhaseStepPolicy = policies.policyOf(stepId)

  override fun directiveFor(stepId: String): String = policies.directiveOf(stepId)

  override fun runStep(
    run: PhaseRun,
    context: FeatureTaskRuntimeRunLoopContext,
    state: PhaseRunState,
  ): PhaseOutcome =
    if (run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW) {
      FeatureTaskRuntimeRunLoopPhaseRunner.runDeclaredReviewDriverCycle(
        context = context,
        run = run,
        taskDirective = directiveFor(run.phaseId),
        driver = reviewDriver(runner, state),
      )
    } else {
      runAgentStep(run, context, state)
    }

  override fun currentExecution(
    stepId: String,
    context: FeatureTaskRuntimeCurrentPhaseExecutionContext,
  ): IdeStatusCurrentPhaseExecution? =
    when (stepId) {
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW ->
        activeReviewPassNumber(context.records[stepId], context.ledger)?.let { pass ->
          IdeStatusCurrentPhaseExecution(
            phaseId = stepId,
            kind = IdeStatusCurrentPhaseExecutionKind.PASS,
            count = pass,
          )
        } ?: attemptPhaseExecution(stepId, context)
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS -> attemptPhaseExecution(stepId, context)
      else -> defaultPhaseExecution(stepId, context)
    }

  private fun activeReviewPassNumber(
    record: FeatureTaskRuntimePhaseRecord?,
    ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
  ): Int? {
    val pass = record?.reviewPassNumber ?: return null
    if (record.status.workflowStepStatus() != WorkflowStepStatus.COMPLETED) return pass
    val latestReviewFixEdge =
      ledger
        .filter {
          it.action == FeatureTaskRuntimePhaseLedgerAction.LOOP_EDGE &&
            it.loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID &&
            it.edgeIteration != null
        }
        .maxByOrNull { it.sequenceNumber }
    val ledgerEdge = latestReviewFixEdge?.edgeIteration
    val reenteredReview =
      record.takeIf {
        it.loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID
      }?.edgeIteration
    return ledgerEdge?.let { edge ->
      reenteredReview?.takeIf { it >= edge }
    }?.let { pass }
  }

  companion object {
    const val ID = "inline"
  }
}
