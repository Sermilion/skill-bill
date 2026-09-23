package skillbill.ports.experiment.pair.model

import skillbill.contracts.JsonCodec

data class ExperimentPairPayload private constructor(
  private val delegate: Map<String, Any?>,
) {
  constructor(payload: Any) : this(
    JsonCodec.anyToStringAnyMap(payload)
      ?: error("Experiment pair payload must be an object."),
  )

  operator fun get(key: String): Any? = delegate[key]

  fun toJson(): String = JsonCodec.mapToJsonString(delegate)
}
