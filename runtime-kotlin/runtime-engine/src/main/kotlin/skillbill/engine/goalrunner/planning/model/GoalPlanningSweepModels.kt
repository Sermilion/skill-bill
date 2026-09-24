package skillbill.engine.goalrunner.planning.model

import skillbill.contracts.workflow.identity.status.GOAL_PLANNING_WAVE_CAP
import skillbill.engine.goalrunner.planning.hydration.expectedProvenance
import skillbill.goalrunner.model.GoalPlanningStatusSnapshot
import skillbill.goalrunner.model.GoalRunnerStopReason
import skillbill.ports.goalrunner.model.GoalPlanningContractProvenance
import skillbill.ports.goalrunner.model.GoalPlanningIdentity
import skillbill.ports.goalrunner.model.GovernedGoalSubtaskDescriptor
import skillbill.ports.goalrunner.runner.model.GoalChildPlanningHydrationRequest
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.taskruntime.model.handoff.task.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputRepairEvidence
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface GoalPlanningSweepOutcome {
  data class PreparedAll(
    val identity: GoalPlanningIdentity? = null,
    val provenance: GoalPlanningContractProvenance? = null,
    val descriptors: List<GovernedGoalSubtaskDescriptor> = emptyList(),
  ) : GoalPlanningSweepOutcome {
    fun hydrationFor(subtaskId: Int) =
      identity?.let { expectedIdentity ->
        val expectedProvenance = requireNotNull(provenance)
        val descriptor = descriptors.singleOrNull { it.subtaskId == subtaskId } ?: return@let null
        GoalChildPlanningHydrationRequest(
          expectedIdentity,
          expectedProvenance,
          descriptor,
        )
      }
  }

  data class Stopped(
    val issueKey: String,
    val currentSubtaskId: Int,
    val reason: GoalRunnerStopReason,
    val blockedReason: String,
    val lastResumableStep: String,
  ) : GoalPlanningSweepOutcome
}

sealed interface GoalPlanningPhaseProduction {
  data class Captured(
    val payload: String,
    val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
    val repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence? = null,
    val agentId: String = "",
  ) : GoalPlanningPhaseProduction

  data class SchemaRejected(
    val reason: String,
    val rejectedOutput: String = "",
    val agentId: String = "",
  ) : GoalPlanningPhaseProduction

  /**
   * The launch itself succeeded and the provider returned nothing usable. Distinct from
   * [SchemaRejected] because there is no rejected output to remediate: relaunching the identical
   * prompt is the whole fix, and the prior-failure remediation text would describe output that was
   * never produced.
   */
  data class EmptyProviderTurn(
    val reason: String,
    val evidence: GoalPlanningEmptyTurnEvidence,
  ) : GoalPlanningPhaseProduction

  /**
   * Schema-valid output whose envelope reported `blocked` or `failed` under a disposition the
   * contract treats as durable. Distinct from [SchemaRejected] because the agent deliberately
   * declined rather than emitting malformed output: the envelope is the only account of why, so it
   * is carried out of the attempt for durable recording before the sweep stops on it.
   */
  data class UnsuccessfulStatus(
    val reason: String,
    val rejectedOutput: String,
    val agentId: String,
    val outcome: GoalPlanningSweepOutcome.Stopped,
  ) : GoalPlanningPhaseProduction

  /**
   * A declined envelope whose disposition the contract marks retryable. Distinct from
   * [UnsuccessfulStatus] because the agent reported a transient condition, not one an operator must
   * clear: blocking the whole goal on it wastes every plan already settled. Distinct from
   * [SchemaRejected] because the output was well-formed, so the retry must not tell the agent its
   * prior output failed the schema gate.
   */
  data class RetryableDecline(
    val reason: String,
    val rejectedOutput: String,
    val agentId: String,
  ) : GoalPlanningPhaseProduction

  data class Stopped(val outcome: GoalPlanningSweepOutcome.Stopped) : GoalPlanningPhaseProduction
}

data class GoalPlanningEmptyTurnEvidence(
  val agentId: String,
  val durationMs: Long,
  val exitStatus: Int?,
  val assistantEventCount: Int?,
  val rawOutputPreview: String?,
) {
  fun summary(): String =
    buildString {
      append("EmptyProviderTurn: agent=")
      append(agentId)
      append(" durationMs=")
      append(durationMs)
      append(" exitStatus=")
      append(exitStatus ?: "none")
      append(" assistantEvents=")
      append(assistantEventCount ?: "unknown")
    }
}

data class GoalPlanningRejectionRecord(
  val parentWorkflowId: String,
  val issueKey: String,
  val phaseId: String,
  val subtaskId: Int,
  val attempt: Int,
  val rule: String,
  val reason: String,
  val agentId: String,
  val rawEvidence: String,
)

data class GoalPlanningBurstSchedule(
  val planFanOutCap: Int,
  val emptyTurnBackoffBase: Duration,
  val emptyTurnBackoffFactor: Int,
  val waitSlice: Duration,
) {
  init {
    require(planFanOutCap >= 1) { "planFanOutCap must be at least 1." }
    require(emptyTurnBackoffBase.isPositive()) { "emptyTurnBackoffBase must be positive." }
    require(emptyTurnBackoffFactor >= 2) { "emptyTurnBackoffFactor must be at least 2." }
    require(waitSlice.isPositive()) { "waitSlice must be positive." }
  }

  fun emptyTurnBackoffAfterAttempt(failedAttempt: Int): Duration {
    require(failedAttempt >= 1) { "failedAttempt must be at least 1." }
    var scale = 1
    repeat(failedAttempt - 1) { scale *= emptyTurnBackoffFactor }
    return emptyTurnBackoffBase * scale
  }

  companion object {
    const val DEFAULT_PLAN_FAN_OUT_CAP: Int = GOAL_PLANNING_WAVE_CAP
    val DEFAULT_EMPTY_TURN_BACKOFF_BASE: Duration = 30.seconds
    const val DEFAULT_EMPTY_TURN_BACKOFF_FACTOR: Int = 2
    val DEFAULT_WAIT_SLICE: Duration = 1.seconds
  }
}

data class GoalPlanningStatusAlignRequest(
  val snapshot: GoalPlanningStatusSnapshot,
  val parentWorkflowId: String,
  val issueKey: String,
  val manifest: DecompositionManifest,
  val repoRoot: Path,
)
