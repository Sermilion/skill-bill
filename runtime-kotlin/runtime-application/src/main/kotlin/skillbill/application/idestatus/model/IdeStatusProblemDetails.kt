package skillbill.application.idestatus.model

class IdeStatusProblemDetails private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  companion object {
    fun from(map: Map<String, Any?>?): IdeStatusProblemDetails? = map?.let { IdeStatusProblemDetails(it.toMap()) }
  }
}
