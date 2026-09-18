package skillbill.infrastructure.sqlite.workflow

import skillbill.error.InvalidFeatureTaskRuntimeWorkerOwnershipSchemaError
import skillbill.infrastructure.sqlite.core.recordDegradedValue
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.featuretask.model.parseFeatureTaskRuntimeWorkerLeaseInstant
import java.time.Instant

internal fun parseWorkerLeaseInstant(
  workflowId: String,
  field: String,
  value: String,
  diagnostics: RuntimeDiagnostics,
): Instant = try {
  parseFeatureTaskRuntimeWorkerLeaseInstant(workflowId, field, value)
} catch (error: InvalidFeatureTaskRuntimeWorkerOwnershipSchemaError) {
  diagnostics.recordDegradedValue(
    seam = "worker_lease.$field",
    expected = "RFC 3339 instant",
    used = value.take(120),
    error = error,
  )
  throw error
}
