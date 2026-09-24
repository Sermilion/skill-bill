package skillbill.engine.featuretask.runner

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.engine.featuretask.lifecycle.continuation.isGoalContinuationRun
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

const val STATUS_RUNNING = "running"
const val STATUS_COMPLETED = "completed"
const val STATUS_BLOCKED = "blocked"

const val STATUS_PAUSED = "paused"

const val STATUS_ABANDONED = "abandoned"
const val BRANCH_SETUP_AGENT_ID = "branch-setup"
const val SCHEMA_GATE_DETAIL_MAX_CHARS = 500

val NON_FILE_MUTATING_PHASES =
  setOf(
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PREPLAN,
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN,
  )

fun serializeTokenData(accumulator: Map<String, Pair<Int, Int>>): Pair<String?, Int?> {
  if (accumulator.isEmpty()) return null to null
  val breakdown =
    accumulator.mapValues { (_, pair) ->
      mapOf("estimated_input_tokens" to pair.first, "estimated_output_tokens" to pair.second)
    }
  val total = accumulator.values.sumOf { (input, output) -> input + output }
  return JsonCodec.mapToJsonString(breakdown) to total
}

fun isFileMutating(phaseId: String): Boolean = phaseId !in NON_FILE_MUTATING_PHASES

fun transitionsFor(request: FeatureTaskRuntimeRunRequest): FeatureTaskRuntimeTransitionDeclaration =
  request.transitionsOverride ?: phasesFor(request).let { phases ->
    FeatureTaskRuntimeTransitionDeclaration(
      forwardPhaseIds = phases,
      backwardEdges =
        FeatureTaskRuntimePhaseWorkflowDefinition.transitions.backwardEdges
          .filter { it.fromPhaseId in phases && it.destinationPhaseId in phases },
      loopOnlyPhaseIds =
        FeatureTaskRuntimePhaseWorkflowDefinition.transitions.loopOnlyPhaseIds
          .filter { it in phases }.toSet(),
      entryGates =
        FeatureTaskRuntimePhaseWorkflowDefinition.transitions.entryGates
          .filter { it.phaseId in phases && it.requiredPhaseId in phases },
      loopOnlySuccessors =
        FeatureTaskRuntimePhaseWorkflowDefinition.transitions.loopOnlySuccessors
          .filterKeys { it in phases }
          .filterValues { it in phases },
    )
  }

fun phasesFor(request: FeatureTaskRuntimeRunRequest): List<String> {
  val phases = FeatureTaskRuntimePhaseWorkflowDefinition.definition.stepIds
  return if (isGoalContinuationRun(request)) {
    phases.takeWhile { it != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PR }
  } else {
    phases
  }
}

internal fun mutatingReconciliationGateReason(
  phaseId: String,
  outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
): String? {
  if (phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT ||
    phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_SIMPLIFY
  ) {
    return null
  }
  if (!FeatureTaskRuntimePhaseWorkflowDefinition.isMutatingPhase(phaseId)) return null

  if ((outputMap[SharedPayloadKeys.STATUS] as? String).workflowStepStatus() != WorkflowStepStatus.COMPLETED) return null
  val producedOutputs = outputMap[SharedPayloadKeys.PRODUCED_OUTPUTS] as? Map<*, *>
  val nestedReconciled = (producedOutputs?.get("reconciled_state") as? Map<*, *>)?.get("reconciled")
  val reconciled = nestedReconciled == true || producedOutputs?.get("reconciled") == true
  return if (reconciled) {
    null
  } else {
    "Mutating phase '$phaseId' reported 'completed' without a reconciliation report proving it " +
      "reconciled the working tree to target: produced_outputs must carry 'reconciled_state' (or a " +
      "'reconciled' entry) with 'reconciled' set to true. The idempotency contract is verified, not " +
      "assumed; a silent skip fails the schema gate."
  }
}

fun boundedSchemaGateDetail(validationReason: String): String =
  if (validationReason.length <= SCHEMA_GATE_DETAIL_MAX_CHARS) {
    validationReason
  } else {
    validationReason.take(SCHEMA_GATE_DETAIL_MAX_CHARS) + "… [truncated]"
  }

fun withSchemaGateDetail(
  policyReason: String,
  validationReason: String,
): String = "$policyReason Last schema-gate failure: ${boundedSchemaGateDetail(validationReason)}"

fun nonRetryingPhaseSchemaBlockReason(phaseId: String): String =
  "Phase '$phaseId' produced schema-invalid output and does not participate in a fix loop; " +
    "the run blocks rather than advancing on invalid output."
