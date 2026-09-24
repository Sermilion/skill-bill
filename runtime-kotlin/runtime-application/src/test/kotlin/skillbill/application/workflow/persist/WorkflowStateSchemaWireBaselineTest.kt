package skillbill.application.workflow.persist

import skillbill.contracts.JsonCodec
import skillbill.ports.workflow.toRecord
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.verify.FeatureVerifyWorkflowDefinition
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkflowStateSchemaWireBaselineTest {
  @Test
  fun `snapshot and step wire bytes remain identical to the captured baselines`() {
    val definition =
      FeatureVerifyWorkflowDefinition.definition.copy(
        workflowName = "bill-feature",
        contractVersion = "0.1",
        defaultInitialStepId = "implement",
        stepIds = listOf("implement"),
        stepLabels = mapOf("implement" to "Implement"),
        requiredArtifactsByStep = mapOf("implement" to emptyList()),
        resumeActions = mapOf("implement" to "Resume."),
        workflowMode = null,
      )
    val engine = WorkflowEngine()
    val record =
      engine.openRecord(definition, "wf-1", "sess", "implement").copy(
        startedAt = Instant.parse("1970-01-01T00:00:00Z"),
        updatedAt = Instant.parse("1970-01-01T00:00:00Z"),
        finishedAt = null,
      )
    val snapshotJson =
      JsonCodec.mapToJsonString(
        WorkflowWireProjections.snapshotMap(engine.snapshotView(definition, record)).toPayload(),
      ) + "\n"
    val stepJson = record.toRecord().stepsJson + "\n"

    assertBaselineBytes("workflow-snapshot-wire.json", snapshotJson)
    assertBaselineBytes("workflow-step-wire.json", stepJson)
  }

  private fun assertBaselineBytes(
    name: String,
    actual: String,
  ) {
    val expected =
      Files.readAllBytes(
        compatibilityRepositoryRoot().resolve(
          ".feature-specs/done/SKILL-351-runtime-domain-boundaries-and-simplicity/" +
            "baselines/$name",
        ),
      )
    assertEquals(expected.toList(), actual.toByteArray().toList(), name)
  }
}

private fun compatibilityRepositoryRoot(): Path {
  var current: Path? = Path.of("").toAbsolutePath().normalize()
  while (current != null) {
    if (Files.isDirectory(current.resolve(".git"))) return current
    current = current.parent
  }
  error("Repository root is not available from the test working directory.")
}
