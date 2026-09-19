package skillbill.infrastructure.sqlite.workflow.featuretask
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimeWorkerOwnershipSchemaError
import skillbill.infrastructure.sqlite.core.ops.degradedValuePreview
import skillbill.infrastructure.sqlite.core.ops.recordDegradedValue
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
    used = value.degradedValuePreview(),
    error = error,
  )
  throw error
}
