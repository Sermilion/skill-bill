package skillbill.engine.goalrunner

import skillbill.ports.agentrun.model.AgentRunProgressEmission
import skillbill.ports.agentrun.model.AgentRunProgressEmitter
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowOutcomeStore
import skillbill.ports.goalrunner.runner.model.GoalRunnerProgressEventRecordRequest
import skillbill.workflow.goal.model.GoalProgressEvent
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException

class GoalRunnerProgressEventEmitter(
  private val outcomeStore: GoalRunnerWorkflowOutcomeStore,
  private val resolveWorkflowId: () -> String?,
  watermarkSeed: Int?,
  private val clock: Clock,
  private val diagnostics: RuntimeDiagnostics,
) : AgentRunProgressEmitter {
  private var sequence: Int = watermarkSeed?.let { it + 1 } ?: 0

  override fun emit(emission: AgentRunProgressEmission) {
    val workflowId = resolveEmitWorkflowId() ?: return
    val event = GoalProgressEvent(
      eventKind = emission.eventKind,
      workflowId = workflowId,

      workflowPhase = "goal_runner_supervision",
      processAlive = emission.processAlive,
      sequenceNumber = sequence++,
      timestamp = clock.instant().toString(),
      operationName = emission.operationName,
      operationKind = emission.operationKind,
      expectedLong = emission.expectedLong,
      outcome = emission.outcome,
    )
    val result = runCatching {
      outcomeStore.recordProgressEvent(
        GoalRunnerProgressEventRecordRequest(workflowId = workflowId, event = event),
      )
    }
    when (val failure = result.exceptionOrNull()) {
      null -> if (!result.getOrThrow()) logBestEffortMissingWorkflow(emission, workflowId)
      is CancellationException -> throw failure
      is InterruptedException -> {
        Thread.currentThread().interrupt()
        throw failure
      }
      else -> logBestEffortFailure(emission, workflowId, failure)
    }
  }

  private fun resolveEmitWorkflowId(): String? = try {
    resolveWorkflowId()?.takeIf(String::isNotBlank)
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (interrupted: InterruptedException) {
    Thread.currentThread().interrupt()
    throw interrupted
  }

  private fun logBestEffortFailure(emission: AgentRunProgressEmission, workflowId: String, error: Throwable) {
    runCatching {
      diagnostics.warning(
        "Best-effort goal progress emit failed: action='${emission.eventKind.wireValue}' " +
          "workflowId='$workflowId' errorType='${error::class.qualifiedName}' " +
          "message='${error.message.orEmpty().take(MAX_DIAGNOSTIC_MESSAGE_LENGTH)}'",
        error,
      )
    }
  }

  private fun logBestEffortMissingWorkflow(emission: AgentRunProgressEmission, workflowId: String) {
    runCatching {
      diagnostics.warning(
        "Best-effort goal progress emit skipped (workflow not found): " +
          "action='${emission.eventKind.wireValue}' workflowId='$workflowId'",
      )
    }
  }

  private companion object {
    const val MAX_DIAGNOSTIC_MESSAGE_LENGTH = 240
  }
}
