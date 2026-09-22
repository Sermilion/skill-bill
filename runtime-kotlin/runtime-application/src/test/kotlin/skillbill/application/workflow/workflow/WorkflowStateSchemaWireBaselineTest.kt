package skillbill.application.workflow.workflow
import skillbill.application.workflow.persist.WorkflowWireProjections
import skillbill.contracts.JsonCodec
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.WorkflowSnapshotValidator
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.verify.FeatureVerifyWorkflowDefinition
import java.nio.file.Files
import java.nio.file.Path
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
    val engine =
      WorkflowEngine(
        object : WorkflowSnapshotValidator {
          override fun validate(
            snapshot: WorkflowStateSnapshot,
            slug: String,
          ) = Unit
        },
      )
    val record =
      engine.openRecord(definition, "wf-1", "sess", "implement").copy(
        startedAt = "1970-01-01T00:00:00Z",
        updatedAt = "1970-01-01T00:00:00Z",
        finishedAt = "",
      )
    val snapshotJson =
      JsonCodec.mapToJsonString(
        WorkflowWireProjections.snapshotMap(engine.snapshotView(definition, record)).toPayload(),
      ) + "\n"
    val stepJson = record.stepsJson + "\n"

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
