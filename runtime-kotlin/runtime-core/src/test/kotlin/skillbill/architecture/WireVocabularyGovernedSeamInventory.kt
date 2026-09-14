package skillbill.architecture

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.workflow.DecompositionManifestSchemaPaths
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
        "infrastructure/fs/contracts/workflow/DecompositionManifest",
        "infrastructure/sqlite/goalrunner/WorkflowGoalRunnerManifest",
        "infrastructure/sqlite/goalrunner/GoalContinuationArtifactCodec",
        "engine/goalrunner/planning/GoalPlanningShared",
        "cli/workflow/WorkflowContinueCliBranchMapsDecomposition",
        "cli/workflow/WorkflowContinueMcpBranchMapsDecomposition",
        "mcp/workflow/WorkflowContinueMcpBranchMapsDecomposition",
      ),
    ),
    GovernedPayloadSeam(
      seamId = "workflow-phase-output-envelope",
      schemaRepoRelativePath = FeatureTaskRuntimePhaseOutputSchemaPaths.REPO_RELATIVE_PATH,
      governedRelativePathMarkers = listOf(
        "workflow/taskruntime/",
        "engine/featuretask/",
        "infrastructure/fs/phaseoutput/",
        "mcp/featuretask/McpFeatureTaskSettlement",
        "application/workflow/WorkflowWire",
        "application/workflow/WorkflowService",
      ),
    ),
  )

  fun closedSchemaPropertyKeys(schemaRepoRelativePath: String): Set<String> =
    when (schemaRepoRelativePath) {
      DecompositionManifestSchemaPaths.REPO_RELATIVE_PATH -> decompositionManifestGovernedKeys(loadRepoSchema(schemaRepoRelativePath))
      FeatureTaskRuntimePhaseOutputSchemaPaths.REPO_RELATIVE_PATH -> phaseOutputEnvelopeGovernedKeys(loadRepoSchema(schemaRepoRelativePath))
      else -> emptySet()
    }

  private fun decompositionManifestGovernedKeys(schema: JsonNode): Set<String> {
    val keys = mutableSetOf<String>()
    keys += propertyNames(schema.path("properties"))
    listOf("subtask", "dependency", "stackBranch", "currentSubtaskIntent").forEach { defName ->
      keys += propertyNames(schema.path("\$defs").path(defName).path("properties"))
    }
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

  fun schemaFieldsMissingKotlinOwner(
    schemaRepoRelativePath: String,
    declaredKeyValues: Set<String>,
  ): List<String> = schemaFieldsMissingKotlinOwner(
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
