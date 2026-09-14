package skillbill.application

import skillbill.application.decomposition.DecompositionManifestWriter
import skillbill.application.decomposition.model.DecompositionManifestWorkflowProjectionInput
import skillbill.application.decomposition.model.DecompositionManifestWriteRequest
import skillbill.contracts.JsonCodec
import skillbill.error.InvalidDecompositionManifestSchemaError
import skillbill.infrastructure.fs.DecompositionManifestValidatorAdapter
import skillbill.infrastructure.fs.FileSystemDecompositionManifestFileStore
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.encodeManifestWireMap
import skillbill.workflow.decomposition.model.DecompositionManifest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * SKILL-52.3 subtask 1: relocated from the runtime-application
 * `DecompositionManifestWriterTest` because these cases assert REAL schema
 * loud-fails through the decomposition write/projection seams. The concrete
 * schema + coherence validators now live in `runtime-infra-fs`, and
 * `runtime-application` must not depend on infra-fs (enforced by
 * `RuntimeGradleModuleLayeringTest`). runtime-core is the composition layer
 * that legitimately wires the real validator adapter, so the end-to-end
 * loud-fail coverage lives here.
 */
class DecompositionManifestWriterValidationTest {
  private val validator: DecompositionManifestValidator = DecompositionManifestValidatorAdapter()
  private val fileStore = FileSystemDecompositionManifestFileStore()
  private val writer = DecompositionManifestWriter()

  @Test
  fun `workflow update rejects schema invalid durable decomposition runtime`() {
    val repoRoot = Files.createTempDirectory("skillbill-invalid-runtime-update")
    val parentSpecPath = repoRoot.resolve(".feature-specs/SKILL-51-decomposition/spec.md")
    Files.createDirectories(parentSpecPath.parent)
    Files.writeString(parentSpecPath, "# Parent spec\n")
    val initial = writer.writeIfDecomposed(
      DecompositionManifestWriteRequest(
        repoRoot = repoRoot,
        parentSpecPath = parentSpecPath,
        planningResult = decompositionPlanningPlan(parentSpecPath),
        baseBranch = "main",
        featureBranch = "feature/SKILL-51-decomposition",
      ),
      validator,
      fileStore,
    )
    assertNotNull(initial)

    val error = assertFailsWith<InvalidDecompositionManifestSchemaError> {
      writer.writeFromWorkflowUpdate(
        DecompositionManifestWorkflowProjectionInput(
          repoRoot = repoRoot,
          existingArtifactsJson = invalidDurableRuntimeArtifactsJson(initial.manifest),
          validator = validator,
          fileStore = fileStore,
        ),
      )
    }

    assertEquals("decomposition_runtime", error.sourceLabel)
    assertContains(error.reason, "contract_version")
    assertContains(error.reason, "offending value: invalid-contract")
  }

  @Test
  fun `workflow projection rejects schema invalid durable decomposition runtime`() {
    val repoRoot = Files.createTempDirectory("skillbill-invalid-runtime-projection")
    val parentSpecPath = repoRoot.resolve(".feature-specs/SKILL-51-decomposition/spec.md")
    Files.createDirectories(parentSpecPath.parent)
    Files.writeString(parentSpecPath, "# Parent spec\n")
    val initial = writer.writeIfDecomposed(
      DecompositionManifestWriteRequest(
        repoRoot = repoRoot,
        parentSpecPath = parentSpecPath,
        planningResult = decompositionPlanningPlan(parentSpecPath),
        baseBranch = "main",
        featureBranch = "feature/SKILL-51-decomposition",
      ),
      validator,
      fileStore,
    )
    assertNotNull(initial)

    val error = assertFailsWith<InvalidDecompositionManifestSchemaError> {
      writer.writeProjectionFromWorkflowState(
        repoRoot = repoRoot,
        artifactsJson = invalidDurableRuntimeArtifactsJson(initial.manifest),
        validator = validator,
        fileStore = fileStore,
      )
    }

    assertEquals("decomposition_runtime", error.sourceLabel)
    assertContains(error.reason, "contract_version")
    assertContains(error.reason, "offending value: invalid-contract")
  }

  private fun invalidDurableRuntimeArtifactsJson(manifest: DecompositionManifest): String {
    val invalidManifest = LinkedHashMap(validator.encodeManifestWireMap(manifest)).apply {
      put("contract_version", "invalid-contract")
    }
    return JsonCodec.mapToJsonString(mapOf("decomposition_runtime" to invalidManifest))
  }
}
