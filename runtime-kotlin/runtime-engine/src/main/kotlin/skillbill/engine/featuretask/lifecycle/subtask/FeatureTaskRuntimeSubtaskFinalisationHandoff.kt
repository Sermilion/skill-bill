package skillbill.engine.featuretask.lifecycle.subtask




import skillbill.engine.featuretask.runner.STATUS_COMPLETED
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.DecompositionManifestPayloadKeys
import skillbill.contracts.decomposition.DecompositionPlanningPayloadKeys
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_CONTRACT_VERSION
import skillbill.contracts.workflow.FeatureTaskRuntimeCommitPushPayloadKeys
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeCommitPushHandoff
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeCommitPushHandoffInvalid
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeCommitPushHandoffResult
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeCommitPushHandoffValid
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeCommitPushReceipt
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition

object FeatureTaskRuntimeSubtaskFinalisationHandoff {
  internal fun runtimeOwnedOutput(receipt: FeatureTaskRuntimeCommitPushReceipt): String {
    val result = linkedMapOf<String, Any?>()
    receipt.commitSha?.trim()?.takeIf(String::isNotBlank)?.let { sha ->
      result[DecompositionManifestPayloadKeys.COMMIT_SHA] = sha
    }
    receipt.branch?.trim()?.takeIf(String::isNotBlank)?.let { branch ->
      result[DecompositionPlanningPayloadKeys.BRANCH] = branch
    }
    receipt.baseBranch?.trim()?.takeIf(String::isNotBlank)?.let { baseBranch ->
      result[DecompositionPlanningPayloadKeys.BASE_BRANCH] = baseBranch
    }
    result[FeatureTaskRuntimeCommitPushPayloadKeys.PUSHED] = receipt.pushed
    return JsonCodec.mapToJsonString(
      mapOf(
        SharedPayloadKeys.CONTRACT_VERSION to FEATURE_TASK_RUNTIME_CONTRACT_VERSION,
        SharedPayloadKeys.PHASE_ID to FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH,
        SharedPayloadKeys.STATUS to STATUS_COMPLETED,
        SharedPayloadKeys.SUMMARY to "Runtime staged every dirty path, committed, and recorded commit_sha.",
        SharedPayloadKeys.PRODUCED_OUTPUTS to mapOf(
          FeatureTaskRuntimeCommitPushPayloadKeys.COMMIT_PUSH_RESULT to result,
        ),
      ),
    )
  }

  internal fun readHandoff(envelope: Map<String, Any?>): FeatureTaskRuntimeCommitPushHandoffResult {
    val result = commitPushResult(envelope)
      ?: return invalid(
        "`produced_outputs.${FeatureTaskRuntimeCommitPushPayloadKeys.COMMIT_PUSH_RESULT}` is absent",
      )
    val message = result[FeatureTaskRuntimeCommitPushPayloadKeys.MESSAGE]
      ?.toString()
      ?.trim()
      ?.takeIf(String::isNotBlank)
      ?: return invalid(
        "`${FeatureTaskRuntimeCommitPushPayloadKeys.COMMIT_PUSH_RESULT}." +
          "${FeatureTaskRuntimeCommitPushPayloadKeys.MESSAGE}` is missing or blank",
      )
    val paths = when {
      !result.containsKey(FeatureTaskRuntimeCommitPushPayloadKeys.CHANGED_PATHS) -> emptyList()
      else -> changedPaths(result) ?: return invalid(
        "`${FeatureTaskRuntimeCommitPushPayloadKeys.COMMIT_PUSH_RESULT}." +
          "${FeatureTaskRuntimeCommitPushPayloadKeys.CHANGED_PATHS}` is not a list of paths",
      )
    }
    return FeatureTaskRuntimeCommitPushHandoffValid(
      FeatureTaskRuntimeCommitPushHandoff(outcomeMessage = message, changedPaths = paths),
    )
  }

  internal fun withCommitSha(envelope: Map<String, Any?>, commitSha: String): Map<String, Any?> {
    val produced = JsonCodec.anyToStringAnyMap(envelope[SharedPayloadKeys.PRODUCED_OUTPUTS])?.toMutableMap()
      ?: return envelope
    val result = JsonCodec.anyToStringAnyMap(
      produced[FeatureTaskRuntimeCommitPushPayloadKeys.COMMIT_PUSH_RESULT],
    )?.toMutableMap()
      ?: return envelope
    result[DecompositionManifestPayloadKeys.COMMIT_SHA] = commitSha
    produced[FeatureTaskRuntimeCommitPushPayloadKeys.COMMIT_PUSH_RESULT] = result
    return envelope.toMutableMap().apply { this[SharedPayloadKeys.PRODUCED_OUTPUTS] = produced }
  }

  private fun changedPaths(result: Map<String, Any?>): List<String>? =
    (result[FeatureTaskRuntimeCommitPushPayloadKeys.CHANGED_PATHS] as? List<*>)
      ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }

  private fun commitPushResult(envelope: Map<String, Any?>): Map<String, Any?>? =
    JsonCodec.anyToStringAnyMap(envelope[SharedPayloadKeys.PRODUCED_OUTPUTS])?.let { produced ->
      JsonCodec.anyToStringAnyMap(produced[FeatureTaskRuntimeCommitPushPayloadKeys.COMMIT_PUSH_RESULT])
    } ?: JsonCodec.anyToStringAnyMap(envelope[FeatureTaskRuntimeCommitPushPayloadKeys.COMMIT_PUSH_RESULT])

  private fun invalid(detail: String) = FeatureTaskRuntimeCommitPushHandoffInvalid(
    "needs_human: commit_push completed but $detail. The runtime performs the commit and push from " +
      "that payload, so without it the subtask would publish the provisional checkpoint subject. " +
      "Re-run commit_push emitting `${FeatureTaskRuntimeCommitPushPayloadKeys.COMMIT_PUSH_RESULT}` with a " +
      "non-blank `${FeatureTaskRuntimeCommitPushPayloadKeys.MESSAGE}` and an enumerated " +
      "`${FeatureTaskRuntimeCommitPushPayloadKeys.CHANGED_PATHS}`.",
  )
}
