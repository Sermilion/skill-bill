package skillbill.ports.taskruntime

import skillbill.ports.taskruntime.model.FeatureTaskRuntimeSharedEvidenceLocatorReadRequest

fun interface FeatureTaskRuntimeSharedEvidenceLocatorReadPort {
  fun readDiffPayload(request: FeatureTaskRuntimeSharedEvidenceLocatorReadRequest): String
}
