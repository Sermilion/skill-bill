package skillbill.ports.taskruntime.model

import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepositoryCheckpoint
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceFileEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeSharedEvidenceHunkEntry
import java.nio.file.Path

data class FeatureTaskRuntimeSharedEvidenceRequest(
  val repoRoot: Path,
  val workflowId: String,
  val checkpoint: FeatureTaskRuntimeRepositoryCheckpoint,
) {
  init {
    require(workflowId.isNotBlank()) {
      "FeatureTaskRuntimeSharedEvidenceRequest.workflowId must be non-blank; an unaddressed " +
        "resolution would share one cache slot across every workflow."
    }
  }
}

data class FeatureTaskRuntimeSharedEvidenceDerivation(
  val baseRef: String?,
  val headRef: String?,
  val files: List<FeatureTaskRuntimeSharedEvidenceFileEntry>,
  val hunks: List<FeatureTaskRuntimeSharedEvidenceHunkEntry>,
  val diffPayload: String,
)

data class FeatureTaskRuntimeSharedEvidenceResolution(
  val artifact: FeatureTaskRuntimeSharedEvidenceArtifact,
  val diffPayload: String,
  val storePath: String? = null,

  val outcome: FeatureTaskRuntimeSharedEvidenceResolveOutcome =
    FeatureTaskRuntimeSharedEvidenceResolveOutcome.DERIVATION,
)

enum class FeatureTaskRuntimeSharedEvidenceResolveOutcome {
  DERIVATION,
  REUSE,
  CHECKPOINT_CHANGE_REDERIVATION,
}

data class FeatureTaskRuntimeSharedEvidenceLocatorReadRequest(
  val repoRoot: Path,
  val storePath: String,
  val payloadFile: String = "diff.patch",
) {
  init {
    require(storePath.isNotBlank()) {
      "FeatureTaskRuntimeSharedEvidenceLocatorReadRequest.storePath must be non-blank."
    }
    require(payloadFile == "diff.patch") {
      "FeatureTaskRuntimeSharedEvidenceLocatorReadRequest.payloadFile must be 'diff.patch'."
    }
  }
}
