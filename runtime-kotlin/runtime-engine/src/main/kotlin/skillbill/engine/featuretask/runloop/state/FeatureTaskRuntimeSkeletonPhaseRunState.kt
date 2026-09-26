package skillbill.engine.featuretask.runloop.state

import skillbill.application.idestatus.AgentActivityStampWriter
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseSettlementTarget
import skillbill.engine.featuretask.phase.core.FeatureTaskPhaseSettlementService
import skillbill.engine.featuretask.slot.PhaseLaunchObservation
import skillbill.engine.featuretask.slot.PhaseRunState
import skillbill.engine.featuretask.slot.PhaseSettledEnvelopeRead
import skillbill.engine.worktreeedit.WorktreeEditJournalWriter
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimeValidationEvidenceSchemaError
import java.nio.file.Path

internal class FeatureTaskRuntimeSkeletonPhaseRunState(
  private val workflowId: String,
  private val parentWorkflowId: String?,
  private val repoRoot: Path,
  private val runState: FeatureTaskRuntimeRunState,
  private val settlementService: FeatureTaskPhaseSettlementService,
  private val activityStampWriter: AgentActivityStampWriter,
  private val worktreeEditJournalWriter: WorktreeEditJournalWriter,
) : PhaseRunState {
  override fun settlementTarget(attempt: Int): FeatureTaskRuntimePhaseSettlementTarget =
    FeatureTaskRuntimePhaseSettlementTarget(workflowId, attempt)

  override fun launchObservation(stepName: String): PhaseLaunchObservation =
    PhaseLaunchObservation(
      activityStampSink = activityStampWriter.sink(workflowId = workflowId, parentWorkflowId = parentWorkflowId),
      worktreeEditObserver =
        worktreeEditJournalWriter.observer(
          repoRoot = repoRoot,
          resolveWorkflowId = { workflowId },
          resolvePhaseId = { stepName },
        ),
    )

  override fun recordTokenUsage(
    stepName: String,
    inputTokens: Int,
    outputTokens: Int,
  ) {
    runState.recordPhaseTokenUsage(stepName, inputTokens, outputTokens)
  }

  override fun settledEnvelope(
    stepName: String,
    target: FeatureTaskRuntimePhaseSettlementTarget,
  ): PhaseSettledEnvelopeRead =
    try {
      settlementService.findEnvelope(target.workflowId, stepName, target.attempt)
        ?.let { PhaseSettledEnvelopeRead.Found(it.envelope) }
        ?: PhaseSettledEnvelopeRead.None
    } catch (error: InvalidFeatureTaskRuntimeValidationEvidenceSchemaError) {
      PhaseSettledEnvelopeRead.Failed(error)
    }
}
