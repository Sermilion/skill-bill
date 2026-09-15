package skillbill.workflow.taskruntime.model

import skillbill.contracts.decomposition.DecompositionPlanningPayloadKeys
import skillbill.error.InvalidWorkflowStateSchemaError

const val FEATURE_TASK_RUNTIME_DECOMPOSE_TERMINAL_ARTIFACT_KEY: String =
  "feature_task_runtime_decompose_terminal"

const val FEATURE_TASK_RUNTIME_DECOMPOSE_GUIDANCE: String =
  "Work the first subtask first, then continue through the ordered spec_subtask_*.md files."

data class FeatureTaskRuntimeDecomposeTerminal(
  val reason: String,
  val parentSpecPath: String,
  val decompositionManifestPath: String,
  val subtaskSpecPaths: List<String>,
) {
  init {
    require(reason.isNotBlank()) { "FeatureTaskRuntimeDecomposeTerminal.reason must be non-blank." }
    require(parentSpecPath.isNotBlank()) { "FeatureTaskRuntimeDecomposeTerminal.parentSpecPath must be non-blank." }
    require(decompositionManifestPath.isNotBlank()) {
      "FeatureTaskRuntimeDecomposeTerminal.decompositionManifestPath must be non-blank."
    }
    require(subtaskSpecPaths.isNotEmpty()) {
      "FeatureTaskRuntimeDecomposeTerminal.subtaskSpecPaths must not be empty."
    }
  }

  val subtaskCount: Int get() = subtaskSpecPaths.size
  internal fun toArtifactMap(): Map<String, Any?> = linkedMapOf(
    "reason" to reason,
    DecompositionPlanningPayloadKeys.PARENT_SPEC_PATH to parentSpecPath,
    "decomposition_manifest_path" to decompositionManifestPath,
    "subtask_spec_paths" to subtaskSpecPaths,
    "subtask_count" to subtaskCount,
    "guidance" to FEATURE_TASK_RUNTIME_DECOMPOSE_GUIDANCE,
  )

  companion object {
    internal fun fromArtifactMap(raw: Map<String, Any?>): FeatureTaskRuntimeDecomposeTerminal =
      FeatureTaskRuntimeDecomposeTerminal(
        reason = raw.requireTerminalStringField("reason"),
        parentSpecPath = raw.requireTerminalStringField(DecompositionPlanningPayloadKeys.PARENT_SPEC_PATH),
        decompositionManifestPath = raw.requireTerminalStringField("decomposition_manifest_path"),
        subtaskSpecPaths = raw.requireTerminalStringListField("subtask_spec_paths"),
      )
  }
}

private fun Map<String, Any?>.requireTerminalStringField(key: String): String {
  val value = this[key]
    ?: terminalSchemaError("Feature-task-runtime decompose-terminal artifact is missing field '$key'.")
  return (value as? String)?.takeIf(String::isNotBlank)
    ?: terminalSchemaError("Feature-task-runtime decompose-terminal field '$key' must be a non-blank string.")
}

private fun Map<String, Any?>.requireTerminalStringListField(key: String): List<String> {
  val value = this[key]
    ?: terminalSchemaError("Feature-task-runtime decompose-terminal artifact is missing list field '$key'.")
  val list = value as? List<*>
    ?: terminalSchemaError("Feature-task-runtime decompose-terminal field '$key' must be a list.")
  return list.mapIndexed { index, element ->
    (element as? String)?.takeIf(String::isNotBlank)
      ?: terminalSchemaError("Feature-task-runtime decompose-terminal field '$key[$index]' must be non-blank.")
  }
}

private fun terminalSchemaError(detail: String): Nothing = throw InvalidWorkflowStateSchemaError(detail)
