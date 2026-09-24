package skillbill.workflow.decomposition.runtime

import skillbill.contracts.JsonCodec
import skillbill.contracts.decomposition.DecompositionManifestProjectionFailurePayloadKeys
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.workflow.engine.model.DurableWorkflowArtifacts

data class DecompositionManifestProjectionFailureArtifact(
  val operation: String,
  val targetPath: String,
)

fun DurableWorkflowArtifacts.decompositionManifestProjectionFailure(): DecompositionManifestProjectionFailureArtifact? {
  if (!containsKey(DECOMPOSITION_MANIFEST_PROJECTION_FAILURE_ARTIFACT_KEY)) return null
  val raw =
    JsonCodec.anyToStringAnyMap(this[DECOMPOSITION_MANIFEST_PROJECTION_FAILURE_ARTIFACT_KEY])
      ?: throw InvalidWorkflowStateSchemaError("Decomposition manifest projection failure must decode to an object.")
  val operation =
    (raw[DecompositionManifestProjectionFailurePayloadKeys.OPERATION] as? String)
      ?.takeIf(String::isNotBlank)
      ?: throw InvalidWorkflowStateSchemaError("Decomposition manifest projection failure operation is required.")
  val targetPath =
    (raw[DecompositionManifestProjectionFailurePayloadKeys.TARGET_PATH] as? String)
      ?.takeIf(String::isNotBlank)
      ?: throw InvalidWorkflowStateSchemaError("Decomposition manifest projection failure target path is required.")
  return DecompositionManifestProjectionFailureArtifact(operation, targetPath)
}
