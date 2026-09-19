package skillbill.application.review.recording
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.runner.request
import skillbill.application.review.parallel.core.code.review.spec.request
import skillbill.application.review.parallel.planning.request
import skillbill.application.review.preparation.request
import skillbill.application.review.preparation.storePath
import skillbill.application.review.review.resolve
import skillbill.application.review.service.review
import skillbill.application.review.spec.resolve
import skillbill.ports.taskruntime.FeatureTaskRuntimeSharedEvidenceLocatorReadPort
import skillbill.ports.taskruntime.FeatureTaskRuntimeSharedEvidenceResolverPort
import java.util.concurrent.ConcurrentHashMap

class RecordingReviewEvidenceStore {
  private val payloads = ConcurrentHashMap<String, String>()
  val reader = FeatureTaskRuntimeSharedEvidenceLocatorReadPort { request ->
    requireNotNull(payloads[request.storePath])
  }
  val resolver = FeatureTaskRuntimeSharedEvidenceResolverPort { request, deriver ->
    val resolution = FeatureTaskRuntimeSharedEvidenceResolverPort.NONE.resolve(request, deriver)
    val address = "recording-review-evidence/${request.checkpoint.fingerprint}"
    payloads[address] = resolution.diffPayload
    resolution.copy(storePath = address)
  }
}
