package skillbill.infrastructure.sqlite.core.migration.goal

import skillbill.agentaddon.model.AgentAddonSelection
import skillbill.agentaddon.model.PersistedAgentAddonSelectionEntry
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.DecompositionManifestPayloadKeys
import skillbill.contracts.workflow.identity.task.FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys
import skillbill.error.shellcontent.InvalidAgentAddonSelectionError
import skillbill.infrastructure.sqlite.core.ops.recordMigrationNormalization
import skillbill.infrastructure.sqlite.core.ops.sqliteDiagnostics
import skillbill.infrastructure.sqlite.workflow.goalrunner.runner.GoalRunnerControlStore
import skillbill.infrastructure.sqlite.workflow.workflow.toFeatureTaskWorkflowStateRecord
import skillbill.ports.goalrunner.runner.model.GoalRunnerOutOfBandAcceptance
import skillbill.ports.goalrunner.runner.model.GoalRunnerReviewPolicy
import skillbill.ports.workflow.model.toSnapshot
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.engine.model.DurableWorkflowArtifactFamily
import java.sql.Connection

internal fun applyLegacyGoalRunnerControlLedgerMigration(connection: Connection) {
  val store = GoalRunnerControlStore(connection)
  connection.prepareStatement(
    """
    SELECT *
    FROM feature_task_workflows
    WHERE mode = 'runtime'
    """.trimIndent(),
  ).use { statement ->
    statement.executeQuery().use { rows ->
      while (rows.next()) {
        val workflow = rows.toFeatureTaskWorkflowStateRecord()
        val workflowId = workflow.workflowId
        val artifacts = workflow.toSnapshot().artifacts
        val movedKeys = mutableListOf<String>()
        if (store.reviewPolicy(workflowId) == null) {
          reviewPolicyFromLegacyArtifacts(artifacts)?.let { policy ->
            store.persistReviewPolicy(workflowId, policy)
            movedKeys += DurableWorkflowArtifactFamily.GOAL_REVIEW_POLICY.label()
          }
        }
        val durableAcceptances = store.outOfBandAcceptances(workflowId)
        val legacyAcceptances =
          outOfBandAcceptancesFromLegacyArtifacts(artifacts)
            .filterKeys { subtaskId -> subtaskId !in durableAcceptances }
        if (legacyAcceptances.isNotEmpty()) {
          legacyAcceptances.values.forEach { acceptance ->
            store.persistOutOfBandAcceptance(workflowId, acceptance)
          }
          movedKeys += DurableWorkflowArtifactFamily.GOAL_OUT_OF_BAND_ACCEPTANCE.label()
        }
        if (movedKeys.isNotEmpty()) {
          connection.sqliteDiagnostics().recordMigrationNormalization(
            seam = "goal_runner_controls.legacy_artifacts",
            parentWorkflowId = workflowId,
            movedArtifactKeys = movedKeys,
          )
        }
      }
    }
  }
}

private fun reviewPolicyFromLegacyArtifacts(artifacts: Map<String, Any?>): GoalRunnerReviewPolicy? {
  val artifactFamily = DurableWorkflowArtifactFamily.GOAL_REVIEW_POLICY
  val raw = artifactFamily.value(artifacts) ?: return null
  val policy =
    JsonCodec.anyToStringAnyMap(raw)
      ?: error("Goal review policy artifact '${artifactFamily.label()}' must be a map.")
  val allowedKeys =
    setOf(
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.CODE_REVIEW_MODE,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.PARALLEL_REVIEW_AGENT,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.AGENT_ADDON_SELECTION,
    )
  policy.keys.forEach { key ->
    require(key in allowedKeys) {
      "Goal review policy artifact '${artifactFamily.label()}' has unsupported field '$key'."
    }
  }
  val mode =
    policy[FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.CODE_REVIEW_MODE] as? String
      ?: error("Goal review policy artifact '${artifactFamily.label()}' is missing code_review_mode.")
  val codeReviewMode =
    try {
      CodeReviewExecutionMode.fromWire(mode)
    } catch (error: IllegalArgumentException) {
      throw IllegalStateException("Goal review policy artifact has invalid code_review_mode '$mode'.", error)
    }
  val agentAddonSelection =
    decodeLegacyAgentAddonSelection(
      policy[FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.AGENT_ADDON_SELECTION],
    )
  return GoalRunnerReviewPolicy(codeReviewMode, agentAddonSelection)
}

private fun outOfBandAcceptancesFromLegacyArtifacts(
  artifacts: Map<String, Any?>,
): Map<Int, GoalRunnerOutOfBandAcceptance> {
  val artifactFamily = DurableWorkflowArtifactFamily.GOAL_OUT_OF_BAND_ACCEPTANCE
  val raw = artifactFamily.value(artifacts) ?: return emptyMap()
  val entries =
    raw as? List<*>
      ?: error("Goal acceptance artifact '${artifactFamily.label()}' must be a list.")
  return entries.associate { element ->
    val entry =
      JsonCodec.anyToStringAnyMap(element)
        ?: error("Goal acceptance artifact '${artifactFamily.label()}' entries must be maps.")
    val acceptance =
      GoalRunnerOutOfBandAcceptance(
        subtaskId =
          (entry[SharedPayloadKeys.SUBTASK_ID] as? Number)?.toInt()
            ?: error("Goal acceptance artifact entry is missing a numeric subtask_id."),
        commitSha =
          entry[DecompositionManifestPayloadKeys.COMMIT_SHA] as? String
            ?: error("Goal acceptance artifact entry is missing commit_sha."),
        reason =
          entry["reason"] as? String
            ?: error("Goal acceptance artifact entry is missing reason."),
        acceptedAt =
          entry["accepted_at"] as? String
            ?: error("Goal acceptance artifact entry is missing accepted_at."),
      )
    acceptance.subtaskId to acceptance
  }
}

private fun decodeLegacyAgentAddonSelection(raw: Any?): AgentAddonSelection {
  val values = raw ?: return AgentAddonSelection()
  val entries =
    values as? List<*>
      ?: throw InvalidAgentAddonSelectionError("Goal review policy agent_addon_selection must be a list.")
  return AgentAddonSelection(entries.mapIndexed(::decodeLegacyAgentAddonSelectionEntry))
}

private fun decodeLegacyAgentAddonSelectionEntry(
  index: Int,
  value: Any?,
): PersistedAgentAddonSelectionEntry {
  val entry =
    JsonCodec.anyToStringAnyMap(value)
      ?: throw InvalidAgentAddonSelectionError(
        "Goal review policy agent_addon_selection entry $index must be a map.",
      )
  val expectedKeys =
    setOf(
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SLUG,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SOURCE_IDENTITY,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_CONTENT_SHA256,
    )
  if (entry.keys != expectedKeys) {
    throw InvalidAgentAddonSelectionError(
      "Goal review policy agent_addon_selection entry $index has invalid fields.",
    )
  }
  return PersistedAgentAddonSelectionEntry(
    requiredLegacyAddonField(entry, index, FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SLUG, "slug"),
    requiredLegacyAddonField(
      entry,
      index,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SOURCE_IDENTITY,
      "source_identity",
    ),
    requiredLegacyAddonField(
      entry,
      index,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_CONTENT_SHA256,
      "content_sha256",
    ),
  )
}

private fun requiredLegacyAddonField(
  entry: Map<String, Any?>,
  index: Int,
  key: String,
  label: String,
): String =
  entry[key] as? String
    ?: throw InvalidAgentAddonSelectionError("Goal review policy add-on entry $index is missing $label.")
