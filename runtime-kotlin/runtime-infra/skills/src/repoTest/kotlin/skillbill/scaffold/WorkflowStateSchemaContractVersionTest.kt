package skillbill.scaffold

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.workflow.WORKFLOW_STATE_CONTRACT_VERSION
import skillbill.contracts.workflow.WorkflowStateSchemaPaths
import skillbill.infrastructure.contracts.workflow.WorkflowStateSchemaValidator
import skillbill.testing.repoRootFromTest
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.verify.FeatureVerifyWorkflowDefinition
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkflowStateSchemaContractVersionTest {
  @Test
  fun `workflow state schema bundled on runtime contracts classpath matches canonical schema`() {
    val schemaFile = repoRootFromTest().resolve(WorkflowStateSchemaPaths.REPO_RELATIVE_PATH)
    assertTrue(Files.isRegularFile(schemaFile), "Canonical schema file is missing at $schemaFile.")
    val canonicalSchema = YAMLMapper().readTree(Files.readString(schemaFile))
    val classpathSchema = loadClasspathSchemaNode()

    assertEquals(
      canonicalSchema,
      classpathSchema,
      "Classpath workflow-state schema at '${WorkflowStateSchemaPaths.CLASSPATH_RESOURCE}' must match " +
        "the canonical schema at ${WorkflowStateSchemaPaths.REPO_RELATIVE_PATH}.",
    )
  }

  @Test
  fun `schema contract_version const matches WORKFLOW_STATE_CONTRACT_VERSION`() {
    val schemaFile = repoRootFromTest().resolve(WorkflowStateSchemaPaths.REPO_RELATIVE_PATH)
    assertTrue(Files.isRegularFile(schemaFile), "Canonical schema file is missing at $schemaFile.")

    val schema: JsonNode = YAMLMapper().readTree(Files.readString(schemaFile))
    val contractVersionNode = schema.path("properties").path("contract_version").path("const")
    assertNotNull(
      contractVersionNode.takeIf { !it.isMissingNode && it.isTextual },
      "Schema must pin properties.contract_version.const as a string; found: $contractVersionNode",
    )
    assertEquals(
      WORKFLOW_STATE_CONTRACT_VERSION,
      contractVersionNode.asText(),
      "Schema contract_version.const must equal WORKFLOW_STATE_CONTRACT_VERSION " +
        "($WORKFLOW_STATE_CONTRACT_VERSION).",
    )
  }

  @Test
  fun `every shipped WorkflowDefinition contractVersion matches WORKFLOW_STATE_CONTRACT_VERSION`() {
    assertEquals(
      WORKFLOW_STATE_CONTRACT_VERSION,
      FeatureVerifyWorkflowDefinition.definition.contractVersion,
      "FeatureVerifyWorkflowDefinition.contractVersion must equal WORKFLOW_STATE_CONTRACT_VERSION " +
        "($WORKFLOW_STATE_CONTRACT_VERSION).",
    )
  }

  @Test
  fun `featureVerify branch enums match FeatureVerifyWorkflowDefinition`() {
    val schema = loadSchemaNode()
    val branch = schema.path("\$defs").path("featureVerifyBranch")
    val definition = FeatureVerifyWorkflowDefinition.definition

    assertBranchStatusesMatch(branch, definition.workflowStatuses, "featureVerifyBranch")
    assertBranchCurrentStepIdsMatch(branch, definition.stepIds.toSet(), "featureVerifyBranch")
    assertBranchStepsStepIdMatch(branch, definition.stepIds.toSet(), "featureVerifyBranch")
  }

  @Test
  fun `featureTaskRuntime branch enums match FeatureTaskRuntimePhaseWorkflowDefinition`() {
    val schema = loadSchemaNode()
    val branch = schema.path("\$defs").path("featureTaskRuntimeBranch")
    val definition = FeatureTaskRuntimePhaseWorkflowDefinition.definition

    assertBranchStatusesMatch(branch, definition.workflowStatuses, "featureTaskRuntimeBranch")
    assertBranchCurrentStepIdsMatch(branch, definition.stepIds.toSet(), "featureTaskRuntimeBranch")
    assertBranchStepsStepIdMatch(branch, definition.stepIds.toSet(), "featureTaskRuntimeBranch")
  }

  @Test
  fun `featureTaskRuntime branch pins the step-id set only, so reordering needs no contract bump`() {
    val branch = loadSchemaNode().path("\$defs").path("featureTaskRuntimeBranch")
    val definition = FeatureTaskRuntimePhaseWorkflowDefinition.definition
    assertBranchStepsStepIdMatch(branch, definition.stepIds.toSet(), "featureTaskRuntimeBranch")

    val steps = branch.path("properties").path("steps")
    assertTrue(
      steps.path("prefixItems").isMissingNode && steps.path("items").path("prefixItems").isMissingNode,
      "Schema featureTaskRuntimeBranch.steps must not declare positional item schemas: a positional " +
        "constraint would make the durable step order part of the contract and a reorder a bump.",
    )
    assertEquals(
      WORKFLOW_STATE_CONTRACT_VERSION,
      definition.contractVersion,
      "Reordering the feature-task-runtime phase pipeline must not move the contract version.",
    )
  }

  @Test
  fun `paused is a non-terminal status on the featureTaskRuntime branch`() {
    val schema = loadSchemaNode()
    val defs = schema.path("\$defs")
    val runtimeStatuses = defs.path("featureTaskRuntimeBranch").path("properties")
      .path("workflow_status").enumStrings()
    assertTrue("paused" in runtimeStatuses, "featureTaskRuntimeBranch.workflow_status must allow 'paused'.")
    assertTrue(
      "paused" in FeatureTaskRuntimePhaseWorkflowDefinition.definition.workflowStatuses,
      "FeatureTaskRuntimePhaseWorkflowDefinition.workflowStatuses must allow 'paused'.",
    )
    assertFalse(
      "paused" in FeatureTaskRuntimePhaseWorkflowDefinition.definition.terminalStatuses,
      "'paused' is resumable by design and must never be consulted as a terminal status.",
    )
    assertFalse(
      "paused" in defs.path("featureVerifyBranch").path("properties").path("workflow_status").enumStrings(),
      "'paused' has no meaning for feature-verify; featureVerifyBranch must not accept it.",
    )
  }

  private fun loadSchemaNode(): JsonNode {
    val schemaFile = repoRootFromTest().resolve(WorkflowStateSchemaPaths.REPO_RELATIVE_PATH)
    assertTrue(Files.isRegularFile(schemaFile), "Canonical schema file is missing at $schemaFile.")
    return YAMLMapper().readTree(Files.readString(schemaFile))
  }

  private fun loadClasspathSchemaNode(): JsonNode {
    val resourceStream = WorkflowStateSchemaValidator::class.java.classLoader
      .getResourceAsStream(WorkflowStateSchemaPaths.CLASSPATH_RESOURCE)
    assertNotNull(
      resourceStream,
      "Canonical workflow-state schema is missing from the classpath at " +
        "'${WorkflowStateSchemaPaths.CLASSPATH_RESOURCE}'. Ensure `copyWorkflowStateSchema` ran before this test.",
    )
    val schemaText = resourceStream.bufferedReader().use { it.readText() }
    return YAMLMapper().readTree(schemaText)
  }

  private fun JsonNode.enumStrings(): Set<String> = path("enum")
    .takeIf { !it.isMissingNode && it.isArray }
    ?.let { node -> node.elements().asSequence().map { it.asText() }.toSet() }
    .orEmpty()

  private fun assertBranchStatusesMatch(branch: JsonNode, expected: Set<String>, branchName: String) {
    val actual = branch.path("properties").path("workflow_status").enumStrings()
    assertEquals(
      expected,
      actual,
      "Schema $branchName.workflow_status enum must equal the Kotlin definition's workflowStatuses set.",
    )
  }

  private fun assertBranchCurrentStepIdsMatch(branch: JsonNode, expected: Set<String>, branchName: String) {
    val actual = branch.path("properties").path("current_step_id").enumStrings()

    val actualWithoutEmpty = actual - ""
    assertEquals(
      expected,
      actualWithoutEmpty,
      "Schema $branchName.current_step_id enum (minus the empty-string sentinel) " +
        "must equal the Kotlin definition's stepIds set.",
    )
    assertTrue(
      "" in actual,
      "Schema $branchName.current_step_id enum must allow the empty-string sentinel for freshly-opened records.",
    )
  }

  private fun assertBranchStepsStepIdMatch(branch: JsonNode, expected: Set<String>, branchName: String) {
    val items = branch.path("properties").path("steps").path("items")

    val allOf = items.path("allOf")
    assertTrue(allOf.isArray, "Schema $branchName.steps.items.allOf must be an array.")
    val stepIdEnum = allOf.elements().asSequence()
      .map { it.path("properties").path("step_id") }
      .firstOrNull { !it.path("enum").isMissingNode }
      ?: error("Schema $branchName.steps.items must declare a step_id enum under allOf[].properties.step_id.")
    val actual = stepIdEnum.enumStrings()
    assertEquals(
      expected,
      actual,
      "Schema $branchName.steps.items.step_id enum must equal the Kotlin definition's stepIds set.",
    )
  }
}
