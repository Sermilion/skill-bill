package skillbill.infrastructure.fs

import skillbill.contracts.JsonCodec
import skillbill.error.InvalidWorkflowStateSchemaError

internal fun requireFeatureTaskRuntimeArtifactMap(payload: Any, sourceLabel: String): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(payload)
    ?: throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime artifact at '$sourceLabel' must decode to an object.",
    )
