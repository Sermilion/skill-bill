package skillbill.ports.idestatus.model

class IdeStatusProblemDetails private constructor(
  private val entries: Map<String, Any?>,
) {
  fun asWireEntries(): Map<String, Any?> = entries

  companion object {
    fun from(map: Map<String, Any?>?): IdeStatusProblemDetails? =
      map?.takeIf { it.isNotEmpty() }?.let { IdeStatusProblemDetails(it.toMap()) }
  }
}
