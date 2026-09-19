package skillbill.workflow.taskruntime.model.review

data class FeatureTaskRuntimeSharedEvidenceArtifact(
  val fingerprint: String,
  val baseRef: String?,
  val headRef: String?,
  val files: List<FeatureTaskRuntimeSharedEvidenceFileEntry>,
  val hunks: List<FeatureTaskRuntimeSharedEvidenceHunkEntry>,
  val diffPayload: FeatureTaskRuntimeSharedEvidenceDiffPayloadRef,
) {
  init {
    require(fingerprint.isNotBlank()) {
      "FeatureTaskRuntimeSharedEvidenceArtifact.fingerprint must be non-blank; evidence that cannot " +
        "name the checkpoint it was derived against can never be safely reused."
    }
  }
}

data class FeatureTaskRuntimeSharedEvidenceFileEntry(
  val path: String,
  val changeKind: String,
) {
  init {
    require(path.isNotBlank()) {
      "FeatureTaskRuntimeSharedEvidenceFileEntry.path must be non-blank."
    }
    require(changeKind.isNotBlank()) {
      "FeatureTaskRuntimeSharedEvidenceFileEntry.changeKind must be non-blank."
    }
  }
}

data class FeatureTaskRuntimeSharedEvidenceHunkEntry(
  val path: String,
  val header: String,
) {
  init {
    require(path.isNotBlank()) {
      "FeatureTaskRuntimeSharedEvidenceHunkEntry.path must be non-blank."
    }
    require(header.isNotBlank()) {
      "FeatureTaskRuntimeSharedEvidenceHunkEntry.header must be non-blank."
    }
  }
}

data class FeatureTaskRuntimeSharedEvidenceDiffPayloadRef(
  val relativePath: String,
  val sizeBytes: Long,
) {
  init {
    require(relativePath.isNotBlank()) {
      "FeatureTaskRuntimeSharedEvidenceDiffPayloadRef.relativePath must be non-blank."
    }
    require(sizeBytes >= 0) {
      "FeatureTaskRuntimeSharedEvidenceDiffPayloadRef.sizeBytes must not be negative, was $sizeBytes."
    }
  }
}
