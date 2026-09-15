package skillbill.engine.goalrunner.planning

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.DecompositionPlanningPayloadKeys
import skillbill.ports.goalrunner.planning.model.GoalPlanningBoundaryHeadingKind
import skillbill.ports.goalrunner.planning.model.GoalPlanningContext
import skillbill.text.sha256HexUtf8

object GoalPlanningSharedContextPacketValidation {
  private val BOUNDARY_MEMORY_FIELDS = setOf("catalog", "truncated")
  private val CATALOG_ENTRY_FIELDS = setOf("heading_id", "source_path", "kind", "heading")
  private val CATALOG_KINDS = setOf(GoalPlanningContext.KIND_HISTORY, GoalPlanningContext.KIND_DECISIONS)
  private val SUBTASK_FIELDS = setOf(
    DecompositionPlanningPayloadKeys.ID,
    DecompositionPlanningPayloadKeys.NAME,
    DecompositionPlanningPayloadKeys.SPEC_PATH,
    "planning_disposition",

    DecompositionPlanningPayloadKeys.DEPENDENCIES,
  )
  private val DEPENDENCY_FIELDS = setOf(
    SharedPayloadKeys.SUBTASK_ID,
    DecompositionPlanningPayloadKeys.OPTIONAL,
    DecompositionPlanningPayloadKeys.SKIPPED,
  )
  private val DISPOSITIONS = setOf("included", DecompositionPlanningPayloadKeys.SKIPPED)

  fun requireValidCatalog(value: Any?) {
    val boundaryMemory = value as? Map<*, *> ?: error("shared context boundary memory is invalid")
    require(boundaryMemory.keys == BOUNDARY_MEMORY_FIELDS) { "shared context boundary memory is invalid" }
    require(boundaryMemory["truncated"] is Boolean) { "shared context boundary memory truncation flag is invalid" }
    val catalog = boundaryMemory["catalog"] as? List<*> ?: error("shared context boundary memory catalog is invalid")
    require(catalog.size <= GoalPlanningContext.MAX_CATALOG_HEADINGS) {
      "shared context boundary memory catalog exceeds the heading cap"
    }
    val headingIds = mutableSetOf<String>()
    for (raw in catalog) {
      validateCatalogEntry(raw, headingIds)
    }
  }

  private fun validateCatalogEntry(raw: Any?, headingIds: MutableSet<String>) {
    val entry = raw as? Map<*, *> ?: error("shared context boundary memory catalog entry is invalid")
    require(entry.keys == CATALOG_ENTRY_FIELDS) { "shared context boundary memory catalog entry fields are invalid" }
    require(entry.values.all { it is String }) { "shared context boundary memory catalog entry is invalid" }
    val sourcePath = entry["source_path"] as String
    require(sourcePath.isNotBlank() && !sourcePath.startsWith("/") && ".." !in sourcePath) {
      "shared context boundary memory source path is invalid"
    }
    require(GoalPlanningBoundaryHeadingKind.fromWire(entry["kind"] as String) in CATALOG_KINDS) {
      "shared context boundary memory kind is invalid"
    }
    require((entry["heading"] as String).length <= GoalPlanningContext.MAX_HEADING_TEXT_CHARS) {
      "shared context boundary memory heading exceeds the length cap"
    }
    require(headingIds.add(entry["heading_id"] as String)) {
      "shared context boundary memory heading ids must be unique"
    }
  }

  fun normalizedSubtasks(value: Any?): List<Map<String, Any?>> {
    val entries = value as? List<*> ?: error("shared context ordered subtasks must be a list")
    return entries.map { entry ->
      val subtask = entry as? Map<*, *> ?: error("shared context ordered subtask must be an object")
      require(subtask.keys == SUBTASK_FIELDS) { "shared context ordered subtask fields are invalid" }
      val id = (subtask[DecompositionPlanningPayloadKeys.ID] as? Number)?.toInt()
        ?: error("shared context ordered subtask id is invalid")
      val name = subtask[DecompositionPlanningPayloadKeys.NAME] as? String
        ?: error("shared context ordered subtask name is invalid")
      val specPath = subtask[DecompositionPlanningPayloadKeys.SPEC_PATH] as? String
        ?: error("shared context ordered subtask spec path is invalid")
      val disposition = subtask["planning_disposition"] as? String
        ?: error("shared context ordered subtask planning disposition is invalid")
      require(disposition in DISPOSITIONS) { "shared context ordered subtask planning disposition is invalid" }
      linkedMapOf(
        DecompositionPlanningPayloadKeys.ID to id,
        DecompositionPlanningPayloadKeys.NAME to name,
        DecompositionPlanningPayloadKeys.SPEC_PATH to specPath,
        "planning_disposition" to disposition,
        DecompositionPlanningPayloadKeys.DEPENDENCIES to normalizedDependencies(
          subtask[DecompositionPlanningPayloadKeys.DEPENDENCIES],
        ),
      )
    }
  }

  private fun normalizedDependencies(value: Any?): List<Map<String, Any?>> {
    val dependencies = value as? List<*> ?: error("shared context subtask dependencies must be a list")
    return dependencies.map { entry ->
      val dependency = entry as? Map<*, *> ?: error("shared context subtask dependency must be an object")
      require(dependency.keys == DEPENDENCY_FIELDS) { "shared context subtask dependency fields are invalid" }
      linkedMapOf(
        SharedPayloadKeys.SUBTASK_ID to (
          (dependency[SharedPayloadKeys.SUBTASK_ID] as? Number)?.toInt()
            ?: error("shared context dependency subtask id is invalid")
          ),
        DecompositionPlanningPayloadKeys.OPTIONAL to (
          dependency[DecompositionPlanningPayloadKeys.OPTIONAL] as? Boolean
            ?: error("shared context dependency optional flag is invalid")
          ),
        DecompositionPlanningPayloadKeys.SKIPPED to (
          dependency[DecompositionPlanningPayloadKeys.SKIPPED] as? Boolean
            ?: error("shared context dependency skipped flag is invalid")
          ),
      )
    }
  }

  fun isStringMap(value: Any?): Boolean =
    value is Map<*, *> && value.keys.all { it is String } && value.values.all { it is String }

  fun digest(packet: Map<String, Any?>): String = sha256HexUtf8(JsonCodec.mapToJsonString(packet))
}
