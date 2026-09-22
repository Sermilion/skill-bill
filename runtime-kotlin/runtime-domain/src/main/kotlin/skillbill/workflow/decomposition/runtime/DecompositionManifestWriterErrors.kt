package skillbill.workflow.decomposition.runtime
import skillbill.error.shellcontent.InvalidDecompositionManifestSchemaError

fun invalidManifest(
  sourceLabel: String,
  reason: String,
): Nothing = throw InvalidDecompositionManifestSchemaError(sourceLabel = sourceLabel, reason = reason)
