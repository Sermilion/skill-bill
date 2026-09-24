package skillbill.application.scaffold

import kotlinx.serialization.json.JsonObject
import skillbill.contracts.JsonCodec
import skillbill.scaffold.model.command.ScaffoldCommandRequest

fun decodeScaffoldPayloadObject(payloadText: String): JsonObject =
  JsonCodec.parseObjectOrNull(payloadText)
    ?: throw IllegalArgumentException("Invalid JSON payload: expected an object.")

fun decodeScaffoldCommandRequest(payloadText: String): ScaffoldCommandRequest =
  decodeScaffoldCommandRequest(decodeScaffoldPayloadObject(payloadText))

fun decodeScaffoldCommandRequest(payload: JsonObject): ScaffoldCommandRequest {
  val map =
    JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(payload))
      ?: throw IllegalArgumentException("Invalid JSON payload: expected an object.")
  val normalized = map.toMutableMap()
  normalized["scaffold_payload_version"] = normalized["scaffold_payload_version"]?.toString()
  return parseScaffoldCommandRequest(normalized)
}
