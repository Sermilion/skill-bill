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
import skillbill.workflow.taskruntime.model.DurableArtifactMapReader
import skillbill.workflow.taskruntime.model.toStringKeyedArtifactMap

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
  val reader = decompositionReader(this, sourceLabel)
  val executionModelValue = reader.requiredString(DecompositionPlanningPayloadKeys.EXECUTION_MODEL)
  val executionModel =
    DecompositionExecutionModel.fromWireValue(executionModelValue)
      ?: invalidDecompositionManifest(
        sourceLabel,
        "${DecompositionPlanningPayloadKeys.EXECUTION_MODEL} '$executionModelValue' is not supported.",
      )
  val specSource = when (
    val rawSpecSource = reader.optionalString(DecompositionPlanningPayloadKeys.SPEC_SOURCE)
  ) {
    null -> SpecSource.LOCAL
    else -> SpecSource.fromWireValue(rawSpecSource)
      ?: invalidDecompositionManifest(
        sourceLabel,
        "${DecompositionPlanningPayloadKeys.SPEC_SOURCE} '$rawSpecSource' is not supported.",
      )
  }
  val subtasks = reader.requiredList(DecompositionPlanningPayloadKeys.SUBTASKS).mapIndexed { index, raw ->
    raw.toDecompositionMap(sourceLabel, "${DecompositionPlanningPayloadKeys.SUBTASKS}[$index]")
      .toDecompositionSubtask(sourceLabel, index)
  }
  val current = reader.requiredNestedObject(DecompositionManifestPayloadKeys.CURRENT_SUBTASK_INTENT)
  return DecompositionManifest(
    contractVersion = reader.requiredString(SharedPayloadKeys.CONTRACT_VERSION),
    issueKey = reader.requiredString(SharedPayloadKeys.ISSUE_KEY),
    featureName = reader.requiredString(DecompositionManifestPayloadKeys.FEATURE_NAME),
    parentSpecPath = reader.requiredString(DecompositionPlanningPayloadKeys.PARENT_SPEC_PATH),
    specSource = specSource,
    status = reader.optionalString(SharedPayloadKeys.STATUS) ?: "pending",
    executionModel = executionModel,
    baseBranch = reader.requiredString(DecompositionPlanningPayloadKeys.BASE_BRANCH),
    featureBranch = reader.optionalString(DecompositionManifestPayloadKeys.FEATURE_BRANCH),
    stackBranches = reader.requiredList(DecompositionPlanningPayloadKeys.STACK_BRANCHES).mapIndexed { index, raw ->
      val item = raw.toDecompositionMap(sourceLabel, "${DecompositionPlanningPayloadKeys.STACK_BRANCHES}[$index]")
      DecompositionStackBranch(
        subtaskId = decompositionReader(item, sourceLabel).requiredInt(SharedPayloadKeys.SUBTASK_ID),
        branch = decompositionReader(item, sourceLabel).requiredString(DecompositionPlanningPayloadKeys.BRANCH),
        baseBranch = decompositionReader(item, sourceLabel).requiredString(DecompositionPlanningPayloadKeys.BASE_BRANCH),
      )
    },
    currentSubtaskIntent = CurrentSubtaskIntent(
      subtaskId = decompositionReader(current, sourceLabel).requiredInt(SharedPayloadKeys.SUBTASK_ID),
      action = decompositionReader(current, sourceLabel).requiredString(DecompositionManifestPayloadKeys.ACTION),
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
    id = decompositionReader(this, sourceLabel).requiredInt(DecompositionPlanningPayloadKeys.ID),
    name = decompositionReader(this, sourceLabel).requiredString(DecompositionPlanningPayloadKeys.NAME),
    specPath = decompositionReader(this, sourceLabel).requiredString(DecompositionPlanningPayloadKeys.SPEC_PATH),
    status = decompositionReader(this, sourceLabel).requiredString(SharedPayloadKeys.STATUS),
    branch = decompositionReader(this, sourceLabel).optionalString(DecompositionPlanningPayloadKeys.BRANCH),
    commitSha = decompositionReader(this, sourceLabel).optionalString(DecompositionManifestPayloadKeys.COMMIT_SHA),
    workflowId = decompositionReader(this, sourceLabel).optionalString(SharedPayloadKeys.WORKFLOW_ID),
    blockedReason = decompositionReader(this, sourceLabel).optionalString(DecompositionManifestPayloadKeys.BLOCKED_REASON),
    lastResumableStep = decompositionReader(this, sourceLabel).optionalString(DecompositionManifestPayloadKeys.LAST_RESUMABLE_STEP),
    linearIssueId = decompositionReader(this, sourceLabel).optionalString(DecompositionPlanningPayloadKeys.LINEAR_ISSUE_ID),
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
    dependencies = decompositionReader(this, sourceLabel)
      .requiredList(DecompositionPlanningPayloadKeys.DEPENDENCIES).mapIndexed { depIndex, dep ->
      val dependency = dep.toDecompositionMap(sourceLabel, "subtasks[$index].dependencies[$depIndex]")
      DecompositionDependency(
        subtaskId = decompositionReader(dependency, sourceLabel).requiredInt(SharedPayloadKeys.SUBTASK_ID),
        optional = decompositionReader(dependency, sourceLabel)
          .requiredBoolean(DecompositionPlanningPayloadKeys.OPTIONAL),
        skipped = decompositionReader(dependency, sourceLabel)
          .requiredBoolean(DecompositionPlanningPayloadKeys.SKIPPED),
      )
    },
  )
}

private fun Any?.toDecompositionMap(sourceLabel: String, fieldPath: String): Map<String, Any?> =
  toStringKeyedArtifactMap { detail -> invalidDecompositionManifest(sourceLabel, "$fieldPath $detail") }

private fun decompositionReader(
  map: Map<String, Any?>,
  sourceLabel: String,
): DurableArtifactMapReader = DurableArtifactMapReader(map) { detail ->
  invalidDecompositionManifest(sourceLabel, detail)
}

private fun invalidDecompositionManifest(sourceLabel: String, reason: String): Nothing =
  throw InvalidDecompositionManifestSchemaError(
    sourceLabel = sourceLabel,
    reason = reason,
    failureCode = "invalid_shape",
  )
