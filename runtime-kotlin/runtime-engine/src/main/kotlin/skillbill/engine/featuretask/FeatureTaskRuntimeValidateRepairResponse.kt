package skillbill.engine.featuretask

import skillbill.engine.featuretask.FeatureTaskRuntimeOutputVerification.auditProseValue

internal object FeatureTaskRuntimeValidateRepairResponse {
  fun extract(capturedText: String): String? {
    FeatureTaskRuntimeRunLoopValidationGate.looseOutputEnvelope(capturedText)
      ?.let { auditProseValue(it) }
      ?.let { return it }
    return capturedText.trim().takeIf { it.isNotBlank() }
  }
}
