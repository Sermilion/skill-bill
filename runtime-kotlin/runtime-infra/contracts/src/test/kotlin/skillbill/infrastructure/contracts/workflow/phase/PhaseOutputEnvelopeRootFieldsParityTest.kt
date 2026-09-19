package skillbill.infrastructure.contracts.workflow.phase
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.workflow.featuretask.FeatureTaskRuntimePhaseOutputSchemaPaths
import skillbill.infrastructure.contracts.phaseoutput.PhaseOutputExpectedShape
import skillbill.infrastructure.contracts.workflow.decomposition.path
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.stream
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.phase.FeatureTaskRuntimePhaseOutputWireSchema
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.validation.path
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.worker.stream
import skillbill.infrastructure.contracts.workflow.featuretask.schema.stream
import skillbill.infrastructure.contracts.workflow.issue.stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PhaseOutputEnvelopeRootFieldsParityTest {
  @Test
  fun `ENVELOPE_ROOT_FIELDS names exactly the schema's declared root properties`() {
    val resourceStream = FeatureTaskRuntimePhaseOutputWireSchema::class.java.classLoader
      .getResourceAsStream(FeatureTaskRuntimePhaseOutputSchemaPaths.CLASSPATH_RESOURCE)
    assertNotNull(
      resourceStream,
      "Canonical phase output schema is missing from the classpath at " +
        "'${FeatureTaskRuntimePhaseOutputSchemaPaths.CLASSPATH_RESOURCE}'.",
    )
    val schema = YAMLMapper().readTree(resourceStream.use { it.readBytes().toString(Charsets.UTF_8) })

    val declared = schema.path("properties").fieldNames().asSequence().toSortedSet()

    assertEquals(
      declared.toList(),
      PhaseOutputExpectedShape.ENVELOPE_ROOT_FIELDS.toSortedSet().toList(),
      "The schema's root properties and ENVELOPE_ROOT_FIELDS have drifted. Add the new root field " +
        "to ENVELOPE_ROOT_FIELDS, or it will be demoted into produced_outputs as a stray key.",
    )
  }

  @Test
  fun `the closed root is what makes a stray key unambiguous`() {
    val schema = FeatureTaskRuntimePhaseOutputWireSchema::class.java.classLoader
      .getResourceAsStream(FeatureTaskRuntimePhaseOutputSchemaPaths.CLASSPATH_RESOURCE)
      .let { stream -> YAMLMapper().readTree(requireNotNull(stream).use { it.readBytes().toString(Charsets.UTF_8) }) }

    assertEquals(
      false,
      schema.path("additionalProperties").asBoolean(true),
      "If the envelope root ever accepts additional properties, a stray key is no longer " +
        "necessarily misplaced and the demote pass must be reconsidered.",
    )
  }
}
