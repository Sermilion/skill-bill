package skillbill.workflow.decomposition

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.DecompositionManifestPayloadKeys
import skillbill.contracts.decomposition.DecompositionPlanningPayloadKeys
import skillbill.error.InvalidDecompositionManifestSchemaError
import skillbill.workflow.decomposition.model.CurrentSubtaskIntent
import skillbill.workflow.decomposition.model.DecompositionDependency
import skillbill.workflow.decomposition.model.DecompositionExecutionModel
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.model.DecompositionStackBranch
import skillbill.workflow.decomposition.model.DecompositionSubtask
import skillbill.workflow.decomposition.model.SpecSource
import java.math.BigDecimal
import java.math.BigInteger

internal object DecompositionManifestWireCodec {
  fun decode(wireMap: Map<String, Any?>, sourceLabel: String = "<in-memory>"): DecompositionManifest =
    wireMap.toDecompositionManifest(sourceLabel)

  fun encode(manifest: DecompositionManifest): Map<String, Any?> = manifest.toWireMap()
}

internal fun DecompositionManifest.toWireMap(): Map<String, Any?> = linkedMapOf(
  SharedPayloadKeys.CONTRACT_VERSION to contractVersion,
  SharedPayloadKeys.ISSUE_KEY to issueKey,
  DecompositionManifestPayloadKeys.FEATURE_NAME to featureName,
  DecompositionPlanningPayloadKeys.PARENT_SPEC_PATH to parentSpecPath,
  SharedPayloadKeys.STATUS to status,
  DecompositionPlanningPayloadKeys.EXECUTION_MODEL to executionModel.wireValue,
  DecompositionPlanningPayloadKeys.BASE_BRANCH to baseBranch,
  DecompositionManifestPayloadKeys.FEATURE_BRANCH to featureBranch,
  DecompositionPlanningPayloadKeys.STACK_BRANCHES to stackBranches.map { branch ->
    linkedMapOf(
      SharedPayloadKeys.SUBTASK_ID to branch.subtaskId,
      DecompositionPlanningPayloadKeys.BRANCH to branch.branch,
      DecompositionPlanningPayloadKeys.BASE_BRANCH to branch.baseBranch,
    )
  },
  DecompositionManifestPayloadKeys.CURRENT_SUBTASK_INTENT to linkedMapOf(
    SharedPayloadKeys.SUBTASK_ID to currentSubtaskIntent.subtaskId,
    DecompositionManifestPayloadKeys.ACTION to currentSubtaskIntent.action,
  ),
  DecompositionPlanningPayloadKeys.SUBTASKS to subtasks.map { subtask ->
    linkedMapOf(
      DecompositionPlanningPayloadKeys.ID to subtask.id,
      DecompositionPlanningPayloadKeys.NAME to subtask.name,
      DecompositionPlanningPayloadKeys.SPEC_PATH to subtask.specPath,
      SharedPayloadKeys.STATUS to subtask.status,
      DecompositionPlanningPayloadKeys.BRANCH to subtask.branch,
      DecompositionManifestPayloadKeys.COMMIT_SHA to subtask.commitSha,
      SharedPayloadKeys.WORKFLOW_ID to subtask.workflowId,
      DecompositionManifestPayloadKeys.BLOCKED_REASON to subtask.blockedReason,
      DecompositionManifestPayloadKeys.LAST_RESUMABLE_STEP to subtask.lastResumableStep,
      DecompositionPlanningPayloadKeys.LINEAR_ISSUE_ID to subtask.linearIssueId,
      DecompositionManifestPayloadKeys.FINALIZING_AGENT_ID to subtask.finalizingAgentId,
      DecompositionManifestPayloadKeys.PARTICIPATING_AGENT_IDS to subtask.participatingAgentIds,
      DecompositionPlanningPayloadKeys.DEPENDENCIES to subtask.dependencies.map { dependency ->
        linkedMapOf(
          SharedPayloadKeys.SUBTASK_ID to dependency.subtaskId,
          DecompositionPlanningPayloadKeys.OPTIONAL to dependency.optional,
          DecompositionPlanningPayloadKeys.SKIPPED to dependency.skipped,
        )
      },
    )
  },
).apply {
  if (specSource != SpecSource.LOCAL) {
    put(DecompositionPlanningPayloadKeys.SPEC_SOURCE, specSource.wireValue)
  }
}

private fun Map<String, Any?>.toDecompositionManifest(sourceLabel: String): DecompositionManifest {
  val executionModelValue = stringValue(DecompositionPlanningPayloadKeys.EXECUTION_MODEL, sourceLabel)
  val executionModel =
    DecompositionExecutionModel.fromWireValue(executionModelValue)
      ?: invalidDecompositionManifest(
        sourceLabel,
        "${DecompositionPlanningPayloadKeys.EXECUTION_MODEL} '$executionModelValue' is not supported.",
      )
  val specSource = when (
    val rawSpecSource = nullableStringValue(DecompositionPlanningPayloadKeys.SPEC_SOURCE, sourceLabel)
  ) {
    null -> SpecSource.LOCAL
    else -> SpecSource.fromWireValue(rawSpecSource)
      ?: invalidDecompositionManifest(
        sourceLabel,
        "${DecompositionPlanningPayloadKeys.SPEC_SOURCE} '$rawSpecSource' is not supported.",
      )
  }
  val subtasks = listValue(DecompositionPlanningPayloadKeys.SUBTASKS).mapIndexed { index, raw ->
    raw.asMap(
      sourceLabel,
      "${DecompositionPlanningPayloadKeys.SUBTASKS}[$index]",
    ).toDecompositionSubtask(sourceLabel, index)
  }
  val current = this[DecompositionManifestPayloadKeys.CURRENT_SUBTASK_INTENT]
    .asMap(sourceLabel, DecompositionManifestPayloadKeys.CURRENT_SUBTASK_INTENT)
  return DecompositionManifest(
    contractVersion = stringValue(SharedPayloadKeys.CONTRACT_VERSION, sourceLabel),
    issueKey = stringValue(SharedPayloadKeys.ISSUE_KEY, sourceLabel),
    featureName = stringValue(DecompositionManifestPayloadKeys.FEATURE_NAME, sourceLabel),
    parentSpecPath = stringValue(DecompositionPlanningPayloadKeys.PARENT_SPEC_PATH, sourceLabel),
    specSource = specSource,
    status = nullableStringValue(SharedPayloadKeys.STATUS, sourceLabel) ?: "pending",
    executionModel = executionModel,
    baseBranch = stringValue(DecompositionPlanningPayloadKeys.BASE_BRANCH, sourceLabel),
    featureBranch = nullableStringValue(DecompositionManifestPayloadKeys.FEATURE_BRANCH, sourceLabel),
    stackBranches = listValue(DecompositionPlanningPayloadKeys.STACK_BRANCHES).mapIndexed { index, raw ->
      val item = raw.asMap(sourceLabel, "${DecompositionPlanningPayloadKeys.STACK_BRANCHES}[$index]")
      DecompositionStackBranch(
        subtaskId = item.intValue(SharedPayloadKeys.SUBTASK_ID, sourceLabel),
        branch = item.stringValue(DecompositionPlanningPayloadKeys.BRANCH, sourceLabel),
        baseBranch = item.stringValue(DecompositionPlanningPayloadKeys.BASE_BRANCH, sourceLabel),
      )
    },
    currentSubtaskIntent = CurrentSubtaskIntent(
      subtaskId = current.intValue(SharedPayloadKeys.SUBTASK_ID, sourceLabel),
      action = current.stringValue(DecompositionManifestPayloadKeys.ACTION, sourceLabel),
    ),
    subtasks = subtasks,
  )
}

private fun Map<String, Any?>.toDecompositionSubtask(sourceLabel: String, index: Int): DecompositionSubtask {
  val participatingAgentIds = when (val value = this[DecompositionManifestPayloadKeys.PARTICIPATING_AGENT_IDS]) {
    null -> emptyList()
    is List<*> -> value.map { element ->
      val str = element as? String
        ?: invalidDecompositionManifest(
          sourceLabel,
          "${DecompositionManifestPayloadKeys.PARTICIPATING_AGENT_IDS} must be a list of strings.",
        )
      str.takeIf(String::isNotBlank)
        ?: invalidDecompositionManifest(
          sourceLabel,
          "${DecompositionManifestPayloadKeys.PARTICIPATING_AGENT_IDS} must be a list of non-blank strings.",
        )
    }
    else -> invalidDecompositionManifest(
      sourceLabel,
      "${DecompositionManifestPayloadKeys.PARTICIPATING_AGENT_IDS} must be a list of strings or null.",
    )
  }
  return DecompositionSubtask(
    id = intValue(DecompositionPlanningPayloadKeys.ID, sourceLabel),
    name = stringValue(DecompositionPlanningPayloadKeys.NAME, sourceLabel),
    specPath = stringValue(DecompositionPlanningPayloadKeys.SPEC_PATH, sourceLabel),
    status = stringValue(SharedPayloadKeys.STATUS, sourceLabel),
    branch = nullableStringValue(DecompositionPlanningPayloadKeys.BRANCH, sourceLabel),
    commitSha = nullableStringValue(DecompositionManifestPayloadKeys.COMMIT_SHA, sourceLabel),
    workflowId = nullableStringValue(SharedPayloadKeys.WORKFLOW_ID, sourceLabel),
    blockedReason = nullableStringValue(DecompositionManifestPayloadKeys.BLOCKED_REASON, sourceLabel),
    lastResumableStep = nullableStringValue(DecompositionManifestPayloadKeys.LAST_RESUMABLE_STEP, sourceLabel),
    linearIssueId = nullableStringValue(DecompositionPlanningPayloadKeys.LINEAR_ISSUE_ID, sourceLabel),
    finalizingAgentId = when (val raw = this[DecompositionManifestPayloadKeys.FINALIZING_AGENT_ID]) {
      null -> null
      is String -> raw.takeIf(String::isNotBlank)
        ?: invalidDecompositionManifest(
          sourceLabel,
          "${DecompositionManifestPayloadKeys.FINALIZING_AGENT_ID} must be a non-blank string or null.",
        )
      else -> invalidDecompositionManifest(
        sourceLabel,
        "${DecompositionManifestPayloadKeys.FINALIZING_AGENT_ID} must be a string or null.",
      )
    },
    participatingAgentIds = participatingAgentIds,
    dependencies = listValue(DecompositionPlanningPayloadKeys.DEPENDENCIES).mapIndexed { depIndex, dep ->
      val dependency = dep.asMap(sourceLabel, "subtasks[$index].dependencies[$depIndex]")
      DecompositionDependency(
        subtaskId = dependency.intValue(SharedPayloadKeys.SUBTASK_ID, sourceLabel),
        optional = dependency.booleanValue(DecompositionPlanningPayloadKeys.OPTIONAL, sourceLabel),
        skipped = dependency.booleanValue(DecompositionPlanningPayloadKeys.SKIPPED, sourceLabel),
      )
    },
  )
}

private fun Map<String, Any?>.stringValue(key: String, sourceLabel: String): String = when (val value = this[key]) {
  is String -> value
  else -> invalidDecompositionManifest(sourceLabel, "$key must be a string.")
}

private fun Map<String, Any?>.nullableStringValue(key: String, sourceLabel: String): String? =
  when (val value = this[key]) {
    null -> null
    is String -> value
    else -> invalidDecompositionManifest(sourceLabel, "$key must be a string or null.")
  }

private fun Map<String, Any?>.intValue(key: String, sourceLabel: String): Int = this[key].asInt(sourceLabel, key)

private fun Map<String, Any?>.booleanValue(key: String, sourceLabel: String): Boolean = when (val value = this[key]) {
  is Boolean -> value
  else -> invalidDecompositionManifest(sourceLabel, "$key must be a boolean.")
}

private fun Map<String, Any?>.listValue(key: String): List<Any?> = (this[key] as? List<*>).orEmpty()

private fun Any?.asMap(sourceLabel: String, fieldPath: String): Map<String, Any?> =
  (this as? Map<*, *>)?.entries?.associateTo(LinkedHashMap<String, Any?>()) { (key, value) ->
    val stringKey = key as? String ?: invalidDecompositionManifest(sourceLabel, "$fieldPath contains a non-string key.")
    stringKey to value
  } ?: invalidDecompositionManifest(sourceLabel, "$fieldPath must be an object.")

private fun Any?.asInt(sourceLabel: String, fieldPath: String): Int = when (this) {
  is Byte -> toInt()
  is Short -> toInt()
  is Int -> this
  is Long -> try {
    Math.toIntExact(this)
  } catch (_: ArithmeticException) {
    null
  }
  is BigInteger -> try {
    intValueExact()
  } catch (_: ArithmeticException) {
    null
  }
  is BigDecimal -> try {
    toBigIntegerExact().intValueExact()
  } catch (_: ArithmeticException) {
    null
  }
  else -> null
} ?: invalidDecompositionManifest(sourceLabel, "$fieldPath must be an exact Kotlin Int.")

private fun invalidDecompositionManifest(sourceLabel: String, reason: String): Nothing =
  throw InvalidDecompositionManifestSchemaError(
    sourceLabel = sourceLabel,
    reason = reason,
    failureCode = "invalid_shape",
  )
