package skillbill.goalrunner.model

import skillbill.contracts.JsonCodec

class GoalRunnerWirePayload private constructor(
  private val delegate: Map<String, Any?>,
) {
  val payload: Map<String, Any?>
    get() = delegate

  operator fun get(key: String): Any? = delegate[key]

  override fun equals(other: Any?): Boolean = other is GoalRunnerWirePayload && delegate == other.delegate

  override fun hashCode(): Int = delegate.hashCode()

  companion object {
    fun from(raw: Any?): GoalRunnerWirePayload =
      GoalRunnerWirePayload(
        JsonCodec.anyToStringAnyMap(raw)
          ?: throw IllegalArgumentException("Goal runner wire payload must decode to a JSON object."),
      )
  }
}
