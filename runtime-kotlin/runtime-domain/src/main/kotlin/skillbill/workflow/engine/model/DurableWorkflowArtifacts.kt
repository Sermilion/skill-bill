package skillbill.workflow.engine.model

import skillbill.contracts.JsonCodec
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError

class DurableWorkflowArtifacts private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  internal fun toMutableMap(): MutableMap<String, Any?> = LinkedHashMap(delegate)

  companion object {
    val EMPTY: DurableWorkflowArtifacts = DurableWorkflowArtifacts(emptyMap())

    fun fromMap(map: Map<String, Any?>): DurableWorkflowArtifacts = DurableWorkflowArtifacts(LinkedHashMap(map))

    fun fromJson(artifactsJson: String): DurableWorkflowArtifacts {
      val parsed =
        JsonCodec.parseObjectOrNull(artifactsJson)
          ?.let(JsonCodec::jsonElementToValue)
          ?.let(JsonCodec::anyToStringAnyMap)
      return DurableWorkflowArtifacts(parsed.orEmpty())
    }

    fun fromAny(raw: Any?): DurableWorkflowArtifacts =
      when (raw) {
        null -> EMPTY
        is DurableWorkflowArtifacts -> raw
        else ->
          fromMap(
            JsonCodec.anyToStringAnyMap(raw)
              ?: throw InvalidWorkflowStateSchemaError(
                "Durable workflow artifacts must decode to an object.",
              ),
          )
      }
  }
}
