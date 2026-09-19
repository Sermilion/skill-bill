package skillbill.engine.featuretask.persist
import skillbill.contracts.JsonCodec
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
fun workflowArtifactEntryMap(entry: Any): Map<String, Any?> = JsonCodec.anyToStringAnyMap(entry)
  ?: throw InvalidWorkflowStateSchemaError("workflow artifact entry must be an object")

fun workflowArtifactEntryMaps(entries: List<Any>): List<Map<String, Any?>> = entries.map(::workflowArtifactEntryMap)
