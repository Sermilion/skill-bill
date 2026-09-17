package skillbill.telemetry.model

class CustomFieldMap private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  override fun equals(other: Any?): Boolean = other is CustomFieldMap && delegate == other.delegate

  override fun hashCode(): Int = delegate.hashCode()

  companion object {
    fun from(map: Map<String, Any?>): CustomFieldMap = CustomFieldMap(LinkedHashMap(map))

    val EMPTY: CustomFieldMap = CustomFieldMap(emptyMap())
  }
}

class TelemetryOpenDocument private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  companion object {
    fun from(map: Map<String, Any?>): TelemetryOpenDocument = TelemetryOpenDocument(LinkedHashMap(map))
  }

  override fun equals(other: Any?): Boolean = other is TelemetryOpenDocument && delegate == other.delegate

  override fun hashCode(): Int = delegate.hashCode()

  override fun toString(): String = delegate.toString()
}
