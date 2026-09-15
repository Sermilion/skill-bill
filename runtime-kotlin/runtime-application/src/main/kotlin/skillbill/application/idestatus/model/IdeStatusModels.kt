package skillbill.application.idestatus.model

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.GOAL_PLANNING_WAVE_CAP
import skillbill.contracts.workflow.IDE_STATUS_CONTRACT_VERSION
import skillbill.goalrunner.model.GoalPlanningStatusState
import skillbill.idestatus.model.AgentActivityLabel
import skillbill.ports.idestatus.IdeStatusWireMap
import skillbill.ports.workflow.model.FeatureTaskRouteScope
import java.nio.file.Path
import java.time.Instant

enum class IdeStatusWorkflowFamily(val wireValue: String) {
  FEATURE_TASK_RUNTIME("feature-task-runtime"),
  FEATURE_VERIFY("feature-verify"),
  FEATURE_GOAL("feature-goal"),
}

enum class IdeStatusLifecycleState(val wireValue: String) {
  ACTIVE("active"),
  PAUSED("paused"),
  BLOCKED("blocked"),
  FAILED("failed"),
  TERMINAL("terminal"),
  IDLE("idle"),
}

enum class IdeStatusFreshness(val wireValue: String) {
  FRESH("fresh"),
  STALE("stale"),
  UNKNOWN("unknown"),
}

const val IDE_STATUS_PAUSE_REASON_LABEL_MAX_LENGTH: Int = 512

enum class IdeStatusPauseReasonCode(val wireValue: String) {
  AWAITING_OPERATOR_DECISION("awaiting_operator_decision"),
  OPERATOR_REQUEST("operator_request"),
  STOP_AFTER_SUBTASK("stop_after_subtask"),
  OPERATOR_STOP("operator_stop"),
  RUNNER_INTERRUPTED("runner_interrupted"),
  ;

  companion object {
    fun fromWire(value: String?): IdeStatusPauseReasonCode? = entries.firstOrNull { it.wireValue == value }
  }
}

data class IdeStatusPauseReason(
  val code: IdeStatusPauseReasonCode,
  val label: String? = null,
) {
  init {
    require(label == null || label.isNotBlank()) {
      "IdeStatusPauseReason.label must be absent or non-blank."
    }
    require(label == null || label.length <= IDE_STATUS_PAUSE_REASON_LABEL_MAX_LENGTH) {
      "IdeStatusPauseReason.label must be bounded to $IDE_STATUS_PAUSE_REASON_LABEL_MAX_LENGTH characters."
    }
  }

  val awaitsOperatorDecision: Boolean
    get() = code == IdeStatusPauseReasonCode.AWAITING_OPERATOR_DECISION

  companion object {
    fun of(code: IdeStatusPauseReasonCode, label: String?): IdeStatusPauseReason {
      val trimmed = label?.trim()?.takeIf(String::isNotBlank)
      return IdeStatusPauseReason(code = code, label = trimmed?.let(::boundedPauseReasonLabel))
    }

    private fun boundedPauseReasonLabel(label: String): String =
      if (label.length <= IDE_STATUS_PAUSE_REASON_LABEL_MAX_LENGTH) {
        label
      } else {
        label.take(IDE_STATUS_PAUSE_REASON_LABEL_MAX_LENGTH - TRUNCATION_MARKER.length) + TRUNCATION_MARKER
      }

    private const val TRUNCATION_MARKER: String = "… [truncated]"
  }
}

enum class IdeStatusProblemCode(val wireValue: String) {
  MISSING_REPOSITORY_IDENTITY("missing_repository_identity"),
  ABSENT_DATABASE("absent_database"),
  NO_MATCHING_WORK("no_matching_work"),
  INCOMPATIBLE_RECORD("incompatible_record"),
  INVALID_REPOSITORY_INPUT("invalid_repository_input"),
  SCHEMA_INCOMPATIBLE("schema_incompatible"),
}

enum class IdeStatusSelectionTier {
  ACTIVE,
  PAUSED,
  BLOCKED,
  FAILED,
  RECENTLY_TERMINAL,
  IDLE,
  ;

  val rank: Int get() = ordinal
}

data class IdeStatusStep(
  val id: String,
  val label: String,
)

data class IdeStatusProgress(
  val completed: Int,
  val total: Int,
) {
  init {
    require(completed >= 0) { "completed must be non-negative." }
    require(total >= 0) { "total must be non-negative." }
  }
}

data class IdeStatusCurrentSubtask(
  val id: String,
  val startedAt: Instant? = null,
  val activeDurationMs: Long? = null,
  val activeDurationAsOf: Instant? = null,
)

data class IdeStatusCurrentModel(
  val model: String,
  val effort: String? = null,

  val phaseId: String? = null,
) {
  init {
    require(model.isNotBlank()) { "currentModel.model must not be blank." }
    effort?.let { require(it.isNotBlank()) { "currentModel.effort must not be blank when present." } }
    phaseId?.let { require(it.isNotBlank()) { "currentModel.phaseId must not be blank when present." } }
  }
}

data class IdeStatusPlanning(
  val state: GoalPlanningStatusState,
  val sharedPreplanPrepared: Boolean,
  val plannedSubtaskCount: Int,
  val totalSubtaskCount: Int,

  val currentPlanningSubtaskId: String? = null,

  val planningWaveSubtaskIds: List<String> = emptyList(),
  val reason: String? = null,
) {
  init {
    require(plannedSubtaskCount >= 0) { "plannedSubtaskCount must be non-negative." }
    require(totalSubtaskCount >= 0) { "totalSubtaskCount must be non-negative." }
    require(planningWaveSubtaskIds.all(String::isNotBlank)) {
      "planningWaveSubtaskIds entries must not be blank."
    }
    require(planningWaveSubtaskIds.distinct().size == planningWaveSubtaskIds.size) {
      "planningWaveSubtaskIds must not repeat a subtask id."
    }
    require(planningWaveSubtaskIds.size <= GOAL_PLANNING_WAVE_CAP) {
      "planningWaveSubtaskIds must hold at most $GOAL_PLANNING_WAVE_CAP ids, was " +
        "${planningWaveSubtaskIds.size}."
    }
  }
}

enum class IdeStatusCurrentPhaseExecutionKind(val wireValue: String) {
  PASS("pass"),
  SEMANTIC_LOOP("semantic_loop"),
  GATE_RUN("gate_run"),
  BOUNDED_EDGE("bounded_edge"),
  ATTEMPT("attempt"),
}

data class IdeStatusCurrentPhaseExecution(
  val phaseId: String,
  val kind: IdeStatusCurrentPhaseExecutionKind,
  val count: Int,
  val total: Int? = null,
) {
  init {
    require(phaseId.isNotBlank()) { "currentPhaseExecution.phaseId must not be blank." }
    require(count >= 1) { "currentPhaseExecution.count must be >= 1, was $count." }
    total?.let {
      require(kind == IdeStatusCurrentPhaseExecutionKind.BOUNDED_EDGE) {
        "currentPhaseExecution.total is allowed only for kind=bounded_edge, was kind=${kind.wireValue}."
      }
      require(it >= 1) { "currentPhaseExecution.total must be >= 1 when present, was $it." }
    }
  }
}

data class IdeStatusProblem(
  val code: IdeStatusProblemCode,
  val message: String,
  val details: IdeStatusProblemDetails? = null,
) {
  init {
    require(message.isNotBlank()) { "problem.message must not be blank." }
  }
}

data class IdeStatusCandidate(
  val workflowId: String,
  val workflowFamily: IdeStatusWorkflowFamily,
  val issueKey: String?,
  val currentState: String,
  val lifecycleState: IdeStatusLifecycleState,
  val selectionTier: IdeStatusSelectionTier,
  val updatedAt: Instant,
  val startedAt: Instant?,
  val routeScope: FeatureTaskRouteScope? = null,
  val isGoalAuthoritative: Boolean = workflowFamily == IdeStatusWorkflowFamily.FEATURE_GOAL,
)

sealed class IdeStatusRepositoryResolution {
  data class Ok(val identity: String, val repoRoot: Path) : IdeStatusRepositoryResolution()
  data class Invalid(val message: String) : IdeStatusRepositoryResolution()
  data class Missing(val message: String) : IdeStatusRepositoryResolution()
}

data class IdeStatusSnapshot(
  val repositoryIdentity: String,
  val lifecycleState: IdeStatusLifecycleState,
  val currentStep: IdeStatusStep,
  val updatedAt: Instant,
  val freshness: IdeStatusFreshness,
  val summary: String,
  val issueKey: String? = null,
  val workflowId: String? = null,
  val workflowFamily: IdeStatusWorkflowFamily? = null,
  val progress: IdeStatusProgress? = null,
  val startedAt: Instant? = null,
  val currentSubtask: IdeStatusCurrentSubtask? = null,

  val currentModel: IdeStatusCurrentModel? = null,

  val planning: IdeStatusPlanning? = null,

  val currentPhaseExecution: IdeStatusCurrentPhaseExecution? = null,

  val pauseRequested: Boolean? = null,
  val pausedAt: Instant? = null,
  val pauseReason: IdeStatusPauseReason? = null,

  val activeDurationMs: Long? = null,
  val activeDurationAsOf: Instant? = null,
  val lastAgentActivityAt: Instant? = null,
  val lastAgentActivityLabel: AgentActivityLabel? = null,
  val problem: IdeStatusProblem? = null,
  val contractVersion: String = IDE_STATUS_CONTRACT_VERSION,
) {
  init {
    require(repositoryIdentity.isNotBlank()) { "repositoryIdentity must not be blank." }
    require(summary.isNotBlank()) { "summary must not be blank." }
    require(currentStep.id.isNotBlank() && currentStep.label.isNotBlank()) {
      "currentStep id/label must not be blank."
    }
  }

  fun toStatusWireMap(): IdeStatusWireMap = IdeStatusWireMap.from(
    buildMap {
      put(SharedPayloadKeys.CONTRACT_VERSION, contractVersion)
      put("repository_identity", repositoryIdentity)
      issueKey?.takeIf(String::isNotBlank)?.let { put(SharedPayloadKeys.ISSUE_KEY, it) }
      workflowId?.takeIf(String::isNotBlank)?.let { put(SharedPayloadKeys.WORKFLOW_ID, it) }
      workflowFamily?.let { put("workflow_family", it.wireValue) }
      put("lifecycle_state", lifecycleState.wireValue)
      put(
        "current_step",
        linkedMapOf(
          "id" to currentStep.id,
          "label" to currentStep.label,
        ),
      )
      progress?.let {
        put(
          "progress",
          linkedMapOf(
            "completed" to it.completed,
            "total" to it.total,
          ),
        )
      }
      startedAt?.let { put("started_at", it.toString()) }
      putCurrentSubtask()
      putCurrentModel()
      planning?.let { put("planning", planningWireMap(it)) }
      putCurrentPhaseExecution()
      pauseRequested?.takeIf { it }?.let { put("pause_requested", true) }
      pausedAt?.let { put("paused_at", it.toString()) }
      putPauseReason()
      putActiveDuration()
      putAgentActivity()
      put("updated_at", updatedAt.toString())
      put("freshness", freshness.wireValue)
      put(SharedPayloadKeys.SUMMARY, summary)
      putProblem()
    },
  )

  private fun MutableMap<String, Any?>.putCurrentSubtask() {
    val subtask = currentSubtask ?: return
    put(
      "current_subtask",
      buildMap {
        put("id", subtask.id)
        subtask.startedAt?.let { put("started_at", it.toString()) }
        subtask.activeDurationMs?.let { put("active_duration_ms", it) }
        subtask.activeDurationAsOf?.let { put("active_duration_as_of", it.toString()) }
      },
    )
  }

  private fun MutableMap<String, Any?>.putCurrentModel() {
    val model = currentModel ?: return
    put(
      "current_model",
      buildMap {
        put("model", model.model)
        model.effort?.let { put("effort", it) }
        model.phaseId?.let { put(SharedPayloadKeys.PHASE_ID, it) }
      },
    )
  }

  private fun MutableMap<String, Any?>.putCurrentPhaseExecution() {
    val execution = currentPhaseExecution ?: return
    put(
      "current_phase_execution",
      buildMap {
        put(SharedPayloadKeys.PHASE_ID, execution.phaseId)
        put("kind", execution.kind.wireValue)
        put("count", execution.count)
        execution.total?.let { put("total", it) }
      },
    )
  }

  private fun MutableMap<String, Any?>.putPauseReason() {
    val reason = pauseReason ?: return
    put(
      "pause_reason",
      buildMap {
        put("code", reason.code.wireValue)
        reason.label?.let { put("label", it) }
      },
    )
  }

  private fun MutableMap<String, Any?>.putActiveDuration() {
    activeDurationMs?.let { put("active_duration_ms", it) }
    activeDurationAsOf?.let { put("active_duration_as_of", it.toString()) }
  }

  private fun MutableMap<String, Any?>.putAgentActivity() {
    val at = lastAgentActivityAt
    val label = lastAgentActivityLabel
    if (at == null || label == null) return
    put("last_agent_activity_at", at.toString())
    put("last_agent_activity_label", label.wireValue)
  }

  private fun MutableMap<String, Any?>.putProblem() {
    val reported = problem ?: return
    put(
      "problem",
      buildMap {
        put("code", reported.code.wireValue)
        put("message", reported.message)
        reported.details?.takeIf { it.isNotEmpty() }?.let { put("details", it) }
      },
    )
  }

  private fun planningWireMap(planning: IdeStatusPlanning): Map<String, Any?> = buildMap {
    put("state", planning.state.wireValue)
    put("shared_preplan_prepared", planning.sharedPreplanPrepared)
    put("planned_subtask_count", planning.plannedSubtaskCount)
    put("total_subtask_count", planning.totalSubtaskCount)
    planning.currentPlanningSubtaskId?.takeIf(String::isNotBlank)
      ?.let { put("current_planning_subtask_id", it) }
    planning.planningWaveSubtaskIds.takeIf { it.isNotEmpty() }
      ?.let { put("planning_wave_subtask_ids", it) }
    planning.reason?.takeIf(String::isNotBlank)?.let { put("reason", it) }
  }
}

data class IdeStatusRequest(
  val repoRoot: String,
  val observedAt: Instant? = null,
) {
  init {
    require(repoRoot.isNotBlank()) { "repoRoot is required." }
  }
}

data class IdeStatusResult(
  val snapshot: IdeStatusSnapshot,
  val exitCode: Int,
)
