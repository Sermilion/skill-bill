package skillbill.architecture

import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeImplementationImportRulesTest {
  @Test
  fun `adapter low-level implementation import scanner catches known bad packages`() {
    val mustBeDetected = listOf(
      "skillbill.infrastructure.sqlite.ReviewDatabase",
      "skillbill.infrastructure.skills.FileSystemScaffoldGateway",
      "skillbill.infrastructure.http.HttpTelemetryClient",
      "skillbill.infrastructure.sqlite.SQLiteDatabaseSessionFactory",
      "skillbill.infrastructure.skills.install.InstallOperations",
      "skillbill.infrastructure.launcher.McpRegistrationOperations",
      "skillbill.infrastructure.skills.nativeagent.NativeAgentOperations",
      "skillbill.review.ReviewRuntime",
      "skillbill.infrastructure.skills.scaffold.ScaffoldService",
      "skillbill.infrastructure.skills.skillremove.SkillRemoveJvmFileSystem",
      "skillbill.telemetry.TelemetryConfigRuntime",
      "skillbill.learnings.LearningsRuntime",
    )
    val mustNotBeDetected = listOf(
      "skillbill.application.ReviewService",
      "skillbill.install.model.InstallPlan",
      "skillbill.learnings.model.LearningScope",
      "skillbill.ports.review.ReviewInputSource",
      "skillbill.scaffold.model.ScaffoldResult",
      "skillbill.telemetry.model.RemoteStatsRequest",
    )

    assertEquals(
      emptyList(),
      mustBeDetected.filterNot(::isRuntimeImplementationImport),
      "Adapter implementation-import scanner must catch known low-level implementation packages.",
    )
    assertEquals(
      emptyList(),
      mustNotBeDetected.filter(::isRuntimeImplementationImport),
      "Adapter implementation-import scanner must allow application services, ports, and public models.",
    )
  }

  @Test
  fun `schema or coherence validator import scanner catches known validators`() {
    val mustBeDetected = listOf(
      "skillbill.infrastructure.contracts.install.InstallPlanSchemaValidator",
      "skillbill.infrastructure.contracts.workflow.WorkflowStateSchemaValidator",
      "skillbill.infrastructure.contracts.workflow.DecompositionManifestSchemaValidator",
      "skillbill.infrastructure.contracts.workflow.DecompositionManifestCoherenceValidator",
      "skillbill.infrastructure.skills.scaffold.PlatformPackSchemaValidator",
      "skillbill.infrastructure.skills.nativeagent.NativeAgentCompositionSchemaValidator",
    )
    val mustNotBeDetected = listOf(

      "skillbill.install.model.InstallPlanWireValidator",
      "skillbill.workflow.decomposition.DecompositionManifestValidator",
      "skillbill.workflow.engine.WorkflowSnapshotValidator",

      "skillbill.application.InstallService",
      "skillbill.infrastructure.contracts.install.InstallPlanSchemaPaths",
    )

    assertEquals(
      emptyList(),
      mustBeDetected.filterNot(::isSchemaOrCoherenceValidatorImport),
      "Schema/coherence validator import scanner must catch every concrete *SchemaValidator/*CoherenceValidator.",
    )
    assertEquals(
      emptyList(),
      mustNotBeDetected.filter(::isSchemaOrCoherenceValidatorImport),
      "Schema/coherence validator import scanner must allow validator ports and unrelated types.",
    )
  }
}
