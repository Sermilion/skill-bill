package skillbill.engine.goalrunner

import skillbill.engine.goalrunner.model.GoalRunnerRunRequest
import skillbill.goalrunner.model.GoalAttemptLaunchOutcome
import skillbill.goalrunner.model.GoalAttemptLedgerAction
import skillbill.goalrunner.model.GoalAttemptLedgerEntry
import skillbill.ports.agentrun.model.AgentRunLaunchFacts
import skillbill.ports.agentrun.model.AgentRunLaunchOutcome
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowOutcomeStore
import skillbill.ports.goalrunner.runner.model.GoalRunnerAttemptLedgerRecordRequest
import skillbill.ports.goalrunner.runner.model.GoalRunnerWorkflowProgress
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException

class GoalRunnerLedgerRecorder(
  private val outcomeStore: GoalRunnerWorkflowOutcomeStore,
  private val request: GoalRunnerRunRequest,
  private val clock: Clock,
  private val diagnostics: RuntimeDiagnostics,
) {

  private val watermarkLoad = try {
    outcomeStore.ledgerSequenceWatermarks(request.issueKey)
  } catch (interrupted: InterruptedException) {
    Thread.currentThread().interrupt()
    throw interrupted
  }
  private var ledgerSequence: Int = watermarkLoad.maxLedgerSequence?.let { it + 1 } ?: 0
  private val cumulativeBackwardEdgeCounts: MutableMap<String, Int> =
    watermarkLoad.backwardEdgeCounts.toMutableMap()

  internal fun recordBackwardEdgeEntry(edge: GoalRunnerBackwardEdge) {
    val key = "${edge.subtaskId}:${edge.loopId}"
    val newCount = (cumulativeBackwardEdgeCounts[key] ?: 0) + edge.edgeIteration.coerceAtLeast(1)
    cumulativeBackwardEdgeCounts[key] = newCount
    recordLedgerEntry(
      GoalRunnerLedgerContext(
        workflowId = edge.workflowId,
        action = GoalAttemptLedgerAction.BACKWARD_EDGE_ENTRY,
        issueKey = edge.issueKey,
        subtaskId = edge.subtaskId,
        progress = edge.progress,
        loopId = edge.loopId,
        cumulativeLoopCount = newCount,
      ),
    )
  }

  internal fun recordLedgerEntry(context: GoalRunnerLedgerContext) {
    val targetWorkflowId = context.workflowId?.takeIf(String::isNotBlank) ?: return
    val facts = context.launchOutcome as? AgentRunLaunchFacts
    val entry = GoalAttemptLedgerEntry(
      action = context.action,
      sequenceNumber = ledgerSequence++,
      timestamp = clock.instant().toString(),
      issueKey = context.issueKey.takeIf(String::isNotBlank),
      subtaskId = context.subtaskId.takeIf { it > 0 },
      previousWorkflowId = targetWorkflowId,
      previousStatus = context.progress?.workflowStatus,
      previousStep = context.progress?.currentStepId,
      blockedReason = context.blockedReason?.takeIf(String::isNotBlank),
      latestLiveness = context.progress?.latestLivenessSignal,
      launchOutcome = facts?.let(::launchFinalStatus),
      timedOut = facts?.timedOut,
      interrupted = facts?.interrupted,

      childSessionPath = facts?.childSessionPath,
      childSessionId = facts?.childSessionId,
      finalReconciledResult = context.finalReconciledResult?.takeIf(String::isNotBlank),
      stopReason = context.stopReason?.takeIf(String::isNotBlank),
      diagnosticClass = context.diagnosticClass?.takeIf(String::isNotBlank),
      currentStep = context.progress?.currentStepId?.takeIf(String::isNotBlank),
      exitStatus = facts?.exitStatus,
      recoverableJsonPresent = context.recoverableJsonPresent,
      nextSafeAction = context.nextSafeAction?.takeIf(String::isNotBlank),
      loopId = context.loopId?.takeIf(String::isNotBlank),
      cumulativeLoopCount = context.cumulativeLoopCount,
      attemptDurationMillis = context.attemptDurationMillis,
      causingLoopEntry = context.causingLoopEntry?.takeIf(String::isNotBlank),
      reAttemptCause = context.reAttemptCause?.takeIf(String::isNotBlank),
      findingsInScope = context.findingsInScope,
    )
    val result = runCatching {
      outcomeStore.recordAttemptLedgerEntry(
        GoalRunnerAttemptLedgerRecordRequest(workflowId = targetWorkflowId, entry = entry),
      )
    }
    when (val failure = result.exceptionOrNull()) {
      null -> if (!result.getOrThrow()) {
        logBestEffortMissingWorkflow(
          "attempt_ledger:${context.action.wireValue}",
          targetWorkflowId,
          context.subtaskId,
        )
      }
      is CancellationException -> throw failure
      is InterruptedException -> {
        Thread.currentThread().interrupt()
        throw failure
      }
      else -> logBestEffortFailure(
        "attempt_ledger:${context.action.wireValue}",
        targetWorkflowId,
        context.subtaskId,
        failure,
      )
    }
  }

  private fun logBestEffortFailure(action: String, workflowId: String, subtaskId: Int, error: Throwable) {
    runCatching {
      diagnostics.warning(
        "Best-effort goal ledger write failed: action='$action' workflowId='$workflowId' subtaskId=$subtaskId " +
          "errorType='${error::class.qualifiedName}' " +
          "message='${error.message.orEmpty().take(MAX_DIAGNOSTIC_MESSAGE_LENGTH)}'",
        error,
      )
    }
  }

  private fun logBestEffortMissingWorkflow(action: String, workflowId: String, subtaskId: Int) {
    runCatching {
      diagnostics.warning(
        "Best-effort goal ledger write skipped (workflow not found): action='$action' " +
          "workflowId='$workflowId' subtaskId=$subtaskId",
      )
    }
  }

  private fun launchFinalStatus(facts: AgentRunLaunchFacts): GoalAttemptLaunchOutcome = when {
    facts.spawnFailed -> GoalAttemptLaunchOutcome.SpawnFailed
    facts.timedOut -> GoalAttemptLaunchOutcome.TimedOut
    facts.interrupted -> GoalAttemptLaunchOutcome.Interrupted
    else -> GoalAttemptLaunchOutcome.Exited(facts.exitStatus)
  }

  private companion object {
    const val MAX_DIAGNOSTIC_MESSAGE_LENGTH = 240
  }
}

internal data class GoalRunnerBackwardEdge(
  val workflowId: String,
  val issueKey: String,
  val subtaskId: Int,
  val loopId: String,
  val edgeIteration: Int,
  val progress: GoalRunnerWorkflowProgress?,
)

internal data class GoalRunnerLedgerContext(
  val workflowId: String?,
  val action: GoalAttemptLedgerAction,
  val issueKey: String,
  val subtaskId: Int,
  val progress: GoalRunnerWorkflowProgress? = null,
  val launchOutcome: AgentRunLaunchOutcome? = null,
  val blockedReason: String? = null,
  val finalReconciledResult: String? = null,
  val stopReason: String? = null,
  val diagnosticClass: String? = null,
  val recoverableJsonPresent: Boolean? = null,
  val nextSafeAction: String? = null,
  val loopId: String? = null,
  val cumulativeLoopCount: Int? = null,
  val attemptDurationMillis: Long? = null,
  val causingLoopEntry: String? = null,
  val reAttemptCause: String? = null,
  val findingsInScope: Int? = null,
)
