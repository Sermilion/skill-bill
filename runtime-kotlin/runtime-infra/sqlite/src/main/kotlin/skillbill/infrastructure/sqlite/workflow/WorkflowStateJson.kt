package skillbill.infrastructure.sqlite.workflow

import skillbill.contracts.JsonCodec
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.error.ShellContentContractException

internal fun decodeWorkflowStringList(rawValue: String?): List<String> {
  if (rawValue.isNullOrBlank()) {
    return emptyList()
  }
  val parsed = try {
    JsonCodec.parseJsonArrayStrict(rawValue.trim())
  } catch (_: ShellContentContractException) {
    throw InvalidWorkflowStateSchemaError("spec_input_types must be a JSON array")
  }
  return parsed.map { element ->
    element as? String ?: throw InvalidWorkflowStateSchemaError("spec_input_types entries must be strings")
  }
}
