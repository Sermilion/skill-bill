package skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.phase
import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.ValidationMessage
import skillbill.infrastructure.contracts.workflow.decomposition.error
import skillbill.infrastructure.contracts.workflow.decomposition.errors
import skillbill.infrastructure.contracts.workflow.decomposition.instance
import skillbill.infrastructure.contracts.workflow.decomposition.instanceLocation
import skillbill.infrastructure.contracts.workflow.decomposition.offendingValue
import skillbill.infrastructure.contracts.workflow.decomposition.path
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.error
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.errors
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.instance
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.sorted
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.checkpoint.error
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.checkpoint.errors
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.checkpoint.instance
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.implementation.error
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.implementation.errors
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.implementation.instance
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.persistence.split
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.error
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.errors
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.instance
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.message
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.projection.error
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.quarantine.errors
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.quarantine.instance
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.shared.error
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.validation.path
import skillbill.infrastructure.contracts.workflow.goal.observability.error
import skillbill.infrastructure.contracts.workflow.goal.observability.errors
import skillbill.infrastructure.contracts.workflow.goal.observability.instance
import skillbill.infrastructure.contracts.workflow.goal.observability.instanceLocation
import skillbill.infrastructure.contracts.workflow.goal.observability.offendingValue
import skillbill.infrastructure.contracts.workflow.goal.planning.error
import skillbill.infrastructure.contracts.workflow.goal.planning.errors
import skillbill.infrastructure.contracts.workflow.goal.planning.instance
import skillbill.infrastructure.contracts.workflow.goal.planning.instanceLocation
import skillbill.infrastructure.contracts.workflow.goal.planning.offendingValue
import skillbill.infrastructure.contracts.workflow.goal.planning.sorted
import skillbill.infrastructure.contracts.workflow.goal.progress.errors
import skillbill.infrastructure.contracts.workflow.goal.progress.instance
import skillbill.infrastructure.contracts.workflow.goal.progress.instanceLocation
import skillbill.infrastructure.contracts.workflow.goal.progress.offendingValue
import skillbill.infrastructure.contracts.workflow.goal.status.errors
import skillbill.infrastructure.contracts.workflow.goal.status.instance
import skillbill.infrastructure.contracts.workflow.goal.status.instanceLocation
import skillbill.infrastructure.contracts.workflow.goal.status.offendingValue
import skillbill.infrastructure.contracts.workflow.schema.error
import skillbill.infrastructure.contracts.workflow.workflow.errors
import skillbill.infrastructure.contracts.workflow.workflow.instance
import skillbill.infrastructure.contracts.workflow.workflow.instanceLocation
import skillbill.infrastructure.contracts.workflow.workflow.offendingValue
import skillbill.infrastructure.contracts.workflow.workflow.sorted

internal fun featureTaskRuntimePhaseOutputDottedFieldPath(instanceLocation: String): String = when {
  instanceLocation.isBlank() || instanceLocation == "/" || instanceLocation == "$" -> ""
  instanceLocation.startsWith("$.") -> instanceLocation.removePrefix("$.")
  instanceLocation.startsWith("$") -> instanceLocation.removePrefix("$").trimStart('.')
  else -> instanceLocation.trimStart('/').replace('/', '.')
}

internal fun extractFeatureTaskRuntimePhaseOutputOffendingValue(instance: JsonNode, instanceLocation: String): String {
  val dotted = featureTaskRuntimePhaseOutputDottedFieldPath(instanceLocation)
  if (dotted.isBlank()) return ""
  var node: JsonNode = instance
  dotted.split('.').forEach { rawSegment ->
    if (rawSegment.isBlank()) return@forEach
    val arrayMatch = Regex("^([^\\[]*)\\[(\\d+)]$").matchEntire(rawSegment)
    when {
      arrayMatch != null -> {
        val (keyPart, indexPart) = arrayMatch.destructured
        if (keyPart.isNotBlank()) {
          node = node.path(keyPart)
        }
        node = node.path(indexPart.toInt())
      }
      node.isArray && rawSegment.toIntOrNull() != null -> {
        node = node.path(rawSegment.toInt())
      }
      else -> {
        node = node.path(rawSegment)
      }
    }
  }
  return when {
    node.isMissingNode -> ""
    node.isValueNode -> node.asText()
    else -> ""
  }
}

internal fun buildSchemaDriftLog(sourceLabel: String, errors: Set<ValidationMessage>): String {
  val parts = errors.sortedWith(featureTaskRuntimePhaseOutputViolationOrdering).take(2).map { error ->
    val location = error.instanceLocation?.toString().orEmpty()
    val fieldPath = featureTaskRuntimePhaseOutputDottedFieldPath(location).ifBlank { "<root>" }
    val constraint = error.message.orEmpty().trim()
    if (constraint.isNotEmpty()) "$fieldPath: $constraint" else fieldPath
  }
  return "Feature-task-runtime phase output failed schema validation: source='$sourceLabel' " +
    "violations=${parts.joinToString(", ")} totalViolations=${errors.size}"
}

internal data class PhaseOutputViolationReasons(val valueBearing: String, val payloadFree: String)

internal fun formatViolationReasons(sorted: List<ValidationMessage>, instance: JsonNode): PhaseOutputViolationReasons {
  val violations = sorted.map { error ->
    val location = error.instanceLocation?.toString().orEmpty()
    val fieldPath = featureTaskRuntimePhaseOutputDottedFieldPath(location).ifBlank { "<root>" }
    val head = "$fieldPath: ${error.message}"
    head to extractFeatureTaskRuntimePhaseOutputOffendingValue(instance, location)
  }
  fun render(includeOffendingValues: Boolean): String =
    violations.joinToString(separator = " | ") { (head, offendingValue) ->
      if (includeOffendingValues && offendingValue.isNotBlank()) {
        "$head — offending value: $offendingValue"
      } else {
        head
      }
    }
  return PhaseOutputViolationReasons(valueBearing = render(true), payloadFree = render(false))
}

internal val featureTaskRuntimePhaseOutputViolationOrdering: Comparator<ValidationMessage> = compareBy(
  { it.instanceLocation?.toString().orEmpty().let { loc -> loc.isBlank() || loc == "$" || loc == "/" } },
  { it.instanceLocation?.toString().orEmpty() },
  { it.message.orEmpty() },
)
