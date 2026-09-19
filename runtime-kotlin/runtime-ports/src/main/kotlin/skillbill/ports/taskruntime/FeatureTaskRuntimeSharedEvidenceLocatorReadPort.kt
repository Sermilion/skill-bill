package skillbill.ports.taskruntime

import skillbill.error.shellcontent.ReviewHunkEvidenceLocatorMissingError
import skillbill.ports.taskruntime.model.FeatureTaskRuntimeSharedEvidenceLocatorReadRequest
fun interface FeatureTaskRuntimeSharedEvidenceLocatorReadPort {
  fun readDiffPayload(request: FeatureTaskRuntimeSharedEvidenceLocatorReadRequest): String

  companion object {
    val NONE: FeatureTaskRuntimeSharedEvidenceLocatorReadPort =
      FeatureTaskRuntimeSharedEvidenceLocatorReadPort { request ->
        throw ReviewHunkEvidenceLocatorMissingError(request.storePath)
      }
  }
}
