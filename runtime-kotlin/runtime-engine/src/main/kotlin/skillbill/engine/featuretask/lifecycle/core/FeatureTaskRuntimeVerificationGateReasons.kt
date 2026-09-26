package skillbill.engine.featuretask.lifecycle.core

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimeFindingVerificationRecordError
import skillbill.goalrunner.subtaskreview.FeatureTaskRuntimeVerificationSignalKeys
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeFindingVerificationDisposition
import skillbill.workflow.taskruntime.model.validation.validateDispositionCoverage
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

object FeatureTaskRuntimeVerificationGateReasons {
  internal fun findingVerificationDisposition(
    phaseId: String,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
    reviewFindingIds: Set<String>,
  ): String? {
    if (phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS || reviewFindingIds.isEmpty()) {
      return null
    }
    val dispositionsKey = FeatureTaskRuntimeVerificationSignalKeys.FINDINGS_VERIFICATION_DISPOSITIONS
    val dispositionsRaw =
      outputMap[SharedPayloadKeys.PRODUCED_OUTPUTS]
        ?.let(JsonCodec::anyToStringAnyMap)
        ?.get(dispositionsKey) as? List<*>
        ?: return "verify_findings reported 'completed' without produced_outputs.$dispositionsKey."
    return runCatching {
      FeatureTaskRuntimeFindingVerificationDisposition.parseList(
        dispositionsRaw,
        "produced_outputs.$dispositionsKey",
      )
    }.fold(
      onSuccess = { validateDispositionCoverage(it, reviewFindingIds) },
      onFailure = { failure ->
        when (failure) {
          is InvalidFeatureTaskRuntimeFindingVerificationRecordError ->
            failure.message ?: "finding verification dispositions are not contract-safe."
          else -> failure.message ?: "finding verification dispositions are not contract-safe."
        }
      },
    )
  }
}
