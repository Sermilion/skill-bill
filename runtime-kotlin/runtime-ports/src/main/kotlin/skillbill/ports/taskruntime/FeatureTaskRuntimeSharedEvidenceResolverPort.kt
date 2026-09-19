package skillbill.ports.taskruntime

import skillbill.ports.taskruntime.model.FeatureTaskRuntimeSharedEvidenceRequest
import skillbill.ports.taskruntime.model.FeatureTaskRuntimeSharedEvidenceResolution
import skillbill.ports.taskruntime.model.FeatureTaskRuntimeSharedEvidenceResolveOutcome
import skillbill.workflow.taskruntime.model.review.FeatureTaskRuntimeSharedEvidenceArtifact
import skillbill.workflow.taskruntime.model.review.FeatureTaskRuntimeSharedEvidenceDiffPayloadRef
fun interface FeatureTaskRuntimeSharedEvidenceResolverPort {
  fun resolve(
    request: FeatureTaskRuntimeSharedEvidenceRequest,
    deriver: FeatureTaskRuntimeSharedEvidenceDeriver,
  ): FeatureTaskRuntimeSharedEvidenceResolution

  companion object {
    val NONE: FeatureTaskRuntimeSharedEvidenceResolverPort =
      FeatureTaskRuntimeSharedEvidenceResolverPort { request, deriver ->
        val derivation = deriver.derive(request.checkpoint)
        FeatureTaskRuntimeSharedEvidenceResolution(
          artifact = FeatureTaskRuntimeSharedEvidenceArtifact(
            fingerprint = request.checkpoint.fingerprint,
            baseRef = derivation.baseRef,
            headRef = derivation.headRef,
            files = derivation.files,
            hunks = derivation.hunks,
            diffPayload = FeatureTaskRuntimeSharedEvidenceDiffPayloadRef(
              relativePath = UNPERSISTED_PAYLOAD_NAME,
              sizeBytes = derivation.diffPayload.toByteArray().size.toLong(),
            ),
          ),
          diffPayload = derivation.diffPayload,
          outcome = FeatureTaskRuntimeSharedEvidenceResolveOutcome.DERIVATION,
        )
      }

    private const val UNPERSISTED_PAYLOAD_NAME = "unpersisted.patch"
  }
}
