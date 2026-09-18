package skillbill.architecture

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.DecompositionManifestBundleJournalSchemaPaths
import skillbill.contracts.review.ReviewFindingPayloadKeys
import skillbill.contracts.review.SqliteReviewTelemetryPayloadKeys
import skillbill.contracts.telemetry.GoalTelemetryPayloadKeys
import skillbill.contracts.telemetry.LifecycleTelemetryPayloadKeys
import skillbill.contracts.telemetry.SqliteLifecycleTelemetryMaterializationPayloadKeys
import skillbill.contracts.workflow.DecompositionManifestSchemaPaths
import skillbill.contracts.workflow.FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys
import skillbill.contracts.workflow.FeatureTaskRuntimePhaseOutputSchemaPaths
import skillbill.testing.repoRootFromTest
import java.nio.file.Files
import kotlin.test.assertTrue

internal data class GovernedPayloadSeam(
  val seamId: String,
  val schemaRepoRelativePath: String,
  val governedRelativePathMarkers: List<String>,
)

internal object WireVocabularyGovernedSeamInventory {
  val seams: List<GovernedPayloadSeam> = listOf(
    GovernedPayloadSeam(
      seamId = "decomposition-manifest",
      schemaRepoRelativePath = DecompositionManifestSchemaPaths.REPO_RELATIVE_PATH,
      governedRelativePathMarkers = listOf(
        "workflow/decomposition/",
        "DecompositionManifest",
        "application/decomposition/",
        "application/workflow/Decomposition",
        "application/featurespec/",
        "infrastructure/contracts/workflow/DecompositionManifest",
        "infrastructure/sqlite/goalrunner/WorkflowGoalRunnerManifest",
        "infrastructure/sqlite/goalrunner/GoalContinuationArtifactCodec",
        "engine/goalrunner/planning/GoalPlanningShared",
        "contracts/goalplanning/GoalPlanningSharedContextPacketPayloadKeys",
        "contracts/workflow/ImplementationReturnContractPayloadKeys",
        "cli/workflow/WorkflowContinueCliBranchMapsDecomposition",
        "cli/workflow/WorkflowContinueMcpBranchMapsDecomposition",
        "mcp/workflow/WorkflowContinueMcpBranchMapsDecomposition",
      ),
    ),
    GovernedPayloadSeam(
      seamId = "decomposition-manifest-bundle-journal",
      schemaRepoRelativePath = DecompositionManifestBundleJournalSchemaPaths.REPO_RELATIVE_PATH,
      governedRelativePathMarkers = listOf(
        "DecompositionManifestBundleJournal",
      ),
    ),
    GovernedPayloadSeam(
      seamId = "workflow-phase-output-envelope",
      schemaRepoRelativePath = FeatureTaskRuntimePhaseOutputSchemaPaths.REPO_RELATIVE_PATH,
      governedRelativePathMarkers = listOf(
        "workflow/taskruntime/",
        "engine/featuretask/",
        "infrastructure/contracts/phaseoutput/",
        "mcp/featuretask/McpFeatureTaskSettlement",
        "application/workflow/WorkflowWire",
        "application/workflow/WorkflowService",
      ),
    ),
    GovernedPayloadSeam(
      seamId = "feature-task-runtime-goal-continuation-artifact",
      schemaRepoRelativePath = GOAL_CONTINUATION_ARTIFACT_SCHEMA_AUTHORITY,
      governedRelativePathMarkers = listOf(
        "taskruntime/model/FeatureTaskRuntimeGoalContinuationArtifact",
        "infrastructure/sqlite/goalrunner/GoalContinuationArtifactCodec",
      ),
    ),
    GovernedPayloadSeam(
      seamId = "sqlite-telemetry-materialization",
      schemaRepoRelativePath = SQLITE_TELEMETRY_MATERIALIZATION_AUTHORITY,
      governedRelativePathMarkers = listOf(
        "infrastructure/sqlite/telemetry/",
      ),
    ),
    GovernedPayloadSeam(
      seamId = "sqlite-review-telemetry",
      schemaRepoRelativePath = SQLITE_REVIEW_TELEMETRY_AUTHORITY,
      governedRelativePathMarkers = listOf(
        "infrastructure/sqlite/review/",
      ),
    ),
  )

  const val GOAL_CONTINUATION_ARTIFACT_SCHEMA_AUTHORITY: String =
    "internal/feature-task-runtime-goal-continuation-artifact"

  const val SQLITE_TELEMETRY_MATERIALIZATION_AUTHORITY: String =
    "internal/sqlite-telemetry-materialization"

  const val SQLITE_REVIEW_TELEMETRY_AUTHORITY: String =
    "internal/sqlite-review-telemetry"

  fun closedSchemaPropertyKeys(schemaRepoRelativePath: String): Set<String> = when (schemaRepoRelativePath) {
    DecompositionManifestSchemaPaths.REPO_RELATIVE_PATH -> decompositionManifestGovernedKeys(
      loadRepoSchema(schemaRepoRelativePath),
    )
    DecompositionManifestBundleJournalSchemaPaths.REPO_RELATIVE_PATH -> bundleJournalGovernedKeys(
      loadRepoSchema(schemaRepoRelativePath),
    )
    FeatureTaskRuntimePhaseOutputSchemaPaths.REPO_RELATIVE_PATH -> phaseOutputEnvelopeGovernedKeys(
      loadRepoSchema(schemaRepoRelativePath),
    )
    GOAL_CONTINUATION_ARTIFACT_SCHEMA_AUTHORITY -> goalContinuationArtifactGovernedKeys()
    SQLITE_TELEMETRY_MATERIALIZATION_AUTHORITY -> sqliteTelemetryMaterializationGovernedKeys()
    SQLITE_REVIEW_TELEMETRY_AUTHORITY -> sqliteReviewTelemetryGovernedKeys()
    else -> emptySet()
  }

  private fun sqliteTelemetryMaterializationGovernedKeys(): Set<String> = payloadKeyValues(
    SharedPayloadKeys::class.java,
    LifecycleTelemetryPayloadKeys::class.java,
    GoalTelemetryPayloadKeys::class.java,
    SqliteLifecycleTelemetryMaterializationPayloadKeys::class.java,
  )

  private fun sqliteReviewTelemetryGovernedKeys(): Set<String> = payloadKeyValues(
    SharedPayloadKeys::class.java,
    SqliteReviewTelemetryPayloadKeys::class.java,
    ReviewFindingPayloadKeys::class.java,
  )

  private fun payloadKeyValues(vararg owners: Class<*>): Set<String> = owners.flatMap { owner ->
    owner.declaredFields
      .filter { field -> field.type == String::class.java }
      .map { field ->
        field.isAccessible = true
        field.get(null) as String
      }
  }.toSet()

  private fun goalContinuationArtifactGovernedKeys(): Set<String> = setOf(
    SharedPayloadKeys.ISSUE_KEY,
    SharedPayloadKeys.SUBTASK_ID,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.SUPPRESS_PR,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.GOAL_BRANCH,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.PARENT_WORKFLOW_ID,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.CODE_REVIEW_MODE,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.VALIDATION_DEPTH,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.QUALITY_GATE_SELECTION,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.PARALLEL_REVIEW_AGENT,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.SUBTASK_NAME,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.AGENT_ADDON_SELECTION,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SLUG,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SOURCE_IDENTITY,
    FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_CONTENT_SHA256,
  )

  private fun decompositionManifestGovernedKeys(schema: JsonNode): Set<String> {
    val keys = mutableSetOf<String>()
    keys += propertyNames(schema.path("properties"))
    listOf("subtask", "dependency", "stackBranch", "currentSubtaskIntent").forEach { defName ->
      keys += propertyNames(schema.path("\$defs").path(defName).path("properties"))
    }
    return keys
  }

  private fun bundleJournalGovernedKeys(schema: JsonNode): Set<String> {
    val keys = mutableSetOf<String>()
    keys += propertyNames(schema.path("properties"))
    keys += propertyNames(schema.path("\$defs").path("entry").path("properties"))
    return keys
  }

  private fun phaseOutputEnvelopeGovernedKeys(schema: JsonNode): Set<String> =
    propertyNames(schema.path("properties")).toSet()

  private fun propertyNames(propertiesNode: JsonNode): List<String> {
    if (!propertiesNode.isObject) return emptyList()
    val names = mutableListOf<String>()
    propertiesNode.fields().forEachRemaining { entry -> names += entry.key }
    return names
  }

  fun schemaFieldsMissingKotlinOwner(schemaRepoRelativePath: String, declaredKeyValues: Set<String>): List<String> =
    schemaFieldsMissingKotlinOwner(
      closedSchemaPropertyKeys(schemaRepoRelativePath),
      schemaRepoRelativePath,
      declaredKeyValues,
    )

  fun schemaFieldsMissingKotlinOwner(
    schemaPropertyKeys: Set<String>,
    schemaAuthority: String,
    declaredKeyValues: Set<String>,
  ): List<String> {
    val missing = schemaPropertyKeys.filter { it !in declaredKeyValues }
    return missing.sorted().map { field ->
      "schema field '$field' in $schemaAuthority has no owning runtime-contracts *Keys const"
    }
  }

  fun fileMatchesGovernedSeam(relativePath: String): Boolean =
    seams.any { seam -> seam.governedRelativePathMarkers.any(relativePath::contains) }

  private fun loadRepoSchema(repoRelativePath: String): JsonNode {
    val path = repoRootFromTest().resolve(repoRelativePath)
    assertTrue(Files.isRegularFile(path), "Missing canonical schema at $path")
    return YAMLMapper().readTree(Files.readString(path))
  }
}
