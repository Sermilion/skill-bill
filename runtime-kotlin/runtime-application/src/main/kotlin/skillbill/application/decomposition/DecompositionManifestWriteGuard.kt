package skillbill.application.decomposition

import skillbill.application.telemetry.sync.failureDetail
import skillbill.contracts.decomposition.DecompositionManifestProjectionFailurePayloadKeys
import skillbill.contracts.decomposition.DecompositionManifestProjectionOperations
import skillbill.workflow.decomposition.runtime.model.DecompositionManifestProjectionOutcome
import skillbill.workflow.decomposition.runtime.model.DecompositionManifestWriteResult
import skillbill.workflow.engine.model.DurableWorkflowArtifactFamily

internal object DecompositionManifestWriteGuard {
  fun requireWritten(
    outcome: DecompositionManifestProjectionOutcome,
    failureDetail: String,
  ): DecompositionManifestWriteResult =
    when (outcome) {
      is DecompositionManifestProjectionOutcome.Written -> outcome.result
      is DecompositionManifestProjectionOutcome.Absent ->
        error("$failureDetail Absent projection is not allowed here.")
      is DecompositionManifestProjectionOutcome.Failed ->
        error(
          "$failureDetail operation=${outcome.operation} path=${outcome.targetPath}",
        )
    }

  fun failureArtifact(outcome: DecompositionManifestProjectionOutcome.Failed): Map<String, String> =
    mapOf(
      DecompositionManifestProjectionFailurePayloadKeys.OPERATION to outcome.operation,
      DecompositionManifestProjectionFailurePayloadKeys.TARGET_PATH to outcome.targetPath,
    )

  fun isRetryableFailure(outcome: DecompositionManifestProjectionOutcome): Boolean =
    outcome is DecompositionManifestProjectionOutcome.Failed

  fun projectionOperationLabel(): String =
    DecompositionManifestProjectionOperations.WRITE_PROJECTION_FROM_WORKFLOW_STATE

  fun failureArtifactKey(): String = DurableWorkflowArtifactFamily.DECOMPOSITION_MANIFEST_PROJECTION_FAILURE.label()
}
