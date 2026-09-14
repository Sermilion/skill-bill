package skillbill.workflow.engine.model

import skillbill.contracts.JsonCodec
import skillbill.error.InvalidWorkflowStateSchemaError

class WorkflowStepUpdates private constructor(
  private val entries: List<Map<String, Any?>>,
) {
  fun asEntries(): List<Map<String, Any?>> = entries

  companion object {
    fun from(entries: List<Map<String, Any?>>?): WorkflowStepUpdates? =
      entries?.let { WorkflowStepUpdates(it) }

    val EMPTY: WorkflowStepUpdates = WorkflowStepUpdates(emptyList())
  }
}

class WorkflowArtifactPatch private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  internal fun toMutableMap(): MutableMap<String, Any?> = LinkedHashMap(delegate)

  companion object {
    fun from(map: Map<String, Any?>?): WorkflowArtifactPatch? =
      map?.let { WorkflowArtifactPatch(LinkedHashMap(it)) }

    val EMPTY: WorkflowArtifactPatch = WorkflowArtifactPatch(emptyMap())
  }
}

class WorkflowContinuationFieldMap private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  companion object {
    fun from(map: Map<String, Any?>): WorkflowContinuationFieldMap =
      WorkflowContinuationFieldMap(LinkedHashMap(map))

    val EMPTY: WorkflowContinuationFieldMap = WorkflowContinuationFieldMap(emptyMap())
  }
}

class WorkflowStepArtifactMap private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  companion object {
    fun from(map: Map<String, Any?>): WorkflowStepArtifactMap =
      WorkflowStepArtifactMap(LinkedHashMap(map))

    val EMPTY: WorkflowStepArtifactMap = WorkflowStepArtifactMap(emptyMap())
  }
}

class WorkflowLaunchProjectionArtifacts private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  companion object {
    fun from(map: Map<String, Any?>): WorkflowLaunchProjectionArtifacts =
      WorkflowLaunchProjectionArtifacts(LinkedHashMap(map))

    val EMPTY: WorkflowLaunchProjectionArtifacts = WorkflowLaunchProjectionArtifacts(emptyMap())
  }
}

class InlineContinuationArtifactValue private constructor(
  val raw: Any?,
) {
  companion object {
    fun from(raw: Any?): InlineContinuationArtifactValue = InlineContinuationArtifactValue(raw)
  }
}

class DecompositionManifestWireMap private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  companion object {
    fun from(map: Map<String, Any?>): DecompositionManifestWireMap =
      DecompositionManifestWireMap(LinkedHashMap(map))

    fun fromAny(raw: Any?): DecompositionManifestWireMap =
      from(
        JsonCodec.anyToStringAnyMap(raw)
          ?: throw InvalidWorkflowStateSchemaError("Decomposition manifest wire map must decode to an object."),
      )
  }
}

class CustomFieldMap private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
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
}

typealias ReviewContextWireMap = skillbill.review.context.ReviewContextWireMap

class GovernedReviewJsonRpcArguments private constructor(
  private val delegate: Map<String, Any?>,
) : Map<String, Any?> by delegate {
  companion object {
    fun from(map: Map<String, Any?>): GovernedReviewJsonRpcArguments =
      GovernedReviewJsonRpcArguments(LinkedHashMap(map))
  }
}
