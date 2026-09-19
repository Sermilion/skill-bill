package skillbill.workflow.taskruntime.model.handoff.task
import skillbill.workflow.taskruntime.model.audit.entries
import skillbill.workflow.taskruntime.model.audit.fromArtifactMap
import skillbill.workflow.taskruntime.model.audit.map
import skillbill.workflow.taskruntime.model.audit.toArtifactMap
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeRepositoryCheckpointPolicy
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactKind
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactValidator
import skillbill.workflow.taskruntime.model.core.fromArtifactMap
import skillbill.workflow.taskruntime.model.core.map
import skillbill.workflow.taskruntime.model.core.task
import skillbill.workflow.taskruntime.model.core.toArtifactMap
import skillbill.workflow.taskruntime.model.feature.entries
import skillbill.workflow.taskruntime.model.feature.fromArtifactMap
import skillbill.workflow.taskruntime.model.feature.map
import skillbill.workflow.taskruntime.model.feature.toArtifactMap
import skillbill.workflow.taskruntime.model.handoff.PhaseHandoffProjectionDeclaration
import skillbill.workflow.taskruntime.model.handoff.PhaseHandoffProjectionDelivery
import skillbill.workflow.taskruntime.model.handoff.PhaseHandoffProjectionShape
import skillbill.workflow.taskruntime.model.handoff.allowsPrivateArtifactReference
import skillbill.workflow.taskruntime.model.handoff.authorizedReferenceKinds
import skillbill.workflow.taskruntime.model.handoff.budget
import skillbill.workflow.taskruntime.model.handoff.checkpointPolicy
import skillbill.workflow.taskruntime.model.handoff.consumerPhaseId
import skillbill.workflow.taskruntime.model.handoff.declaredFieldNames
import skillbill.workflow.taskruntime.model.handoff.delivery
import skillbill.workflow.taskruntime.model.handoff.fromArtifactMap
import skillbill.workflow.taskruntime.model.handoff.inlineAlternative
import skillbill.workflow.taskruntime.model.handoff.name
import skillbill.workflow.taskruntime.model.handoff.producerIteration
import skillbill.workflow.taskruntime.model.handoff.projectionContractId
import skillbill.workflow.taskruntime.model.handoff.projectionContractVersion
import skillbill.workflow.taskruntime.model.handoff.projectionName
import skillbill.workflow.taskruntime.model.handoff.promptVisibility
import skillbill.workflow.taskruntime.model.handoff.required
import skillbill.workflow.taskruntime.model.handoff.shape
import skillbill.workflow.taskruntime.model.handoff.sourceRef
import skillbill.workflow.taskruntime.model.handoff.toArtifactMap
import skillbill.workflow.taskruntime.model.persistence.artifact.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.fromArtifactMap
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.toArtifactMap
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.fromArtifactMap
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.toArtifactMap
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.fromArtifactMap
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.toArtifactMap
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.map
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.FeatureTaskRuntimeHandoffPromptVisibility
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.entries
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.keys
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.toArtifactMap
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.wireValue
import skillbill.workflow.taskruntime.model.phase.checkpointFingerprint
import skillbill.workflow.taskruntime.model.phase.consumerPhaseId
import skillbill.workflow.taskruntime.model.phase.fromArtifactMap
import skillbill.workflow.taskruntime.model.phase.map
import skillbill.workflow.taskruntime.model.phase.name
import skillbill.workflow.taskruntime.model.phase.outcome
import skillbill.workflow.taskruntime.model.phase.repositoryCheckpointFingerprint
import skillbill.workflow.taskruntime.model.phase.required
import skillbill.workflow.taskruntime.model.phase.toArtifactMap
import skillbill.workflow.taskruntime.model.phase.workflowId
import skillbill.workflow.taskruntime.model.repair.task.budget
import skillbill.workflow.taskruntime.model.repair.task.entries
import skillbill.workflow.taskruntime.model.repair.task.fromArtifactMap
import skillbill.workflow.taskruntime.model.repair.task.outcome
import skillbill.workflow.taskruntime.model.repair.task.toArtifactMap
import skillbill.workflow.taskruntime.model.validation.entries
import skillbill.workflow.taskruntime.model.validation.fromArtifactMap
import skillbill.workflow.taskruntime.model.validation.map
import skillbill.workflow.taskruntime.model.validation.outcome
import skillbill.workflow.taskruntime.model.validation.toArtifactMap
import skillbill.workflow.taskruntime.model.validation.wireValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
class FeatureTaskRuntimeHandoffFoundationModelsTest {
  @Test
  fun `declaration wire mapping matches the closed phase handoff contract`() {
    val declaration = PhaseHandoffProjectionDeclaration(
      consumerPhaseId = "audit",
      sourceRef = FeatureTaskRuntimeHandoffSourceRef.UpstreamPhaseOutput("implement"),
      shape = PhaseHandoffProjectionShape(
        projectionName = "implement_prose",
        projectionContractId = "feature_task_runtime.phase_prose",
        projectionContractVersion = "0.1",
        promptVisibility = FeatureTaskRuntimeHandoffPromptVisibility.PROMPT_VISIBLE,
        budget = FeatureTaskRuntimeHandoffProjectionBudget(4096, 16),
        declaredFieldNames = listOf("value", "directive"),
      ),
      delivery = PhaseHandoffProjectionDelivery(
        checkpointPolicy = FeatureTaskRuntimeRepositoryCheckpointPolicy.MUST_MATCH,
        required = false,
        allowsPrivateArtifactReference = true,
        producerIteration = FeatureTaskRuntimeProducerIteration("implement", 4),
        inlineAlternative = FeatureTaskRuntimeCompactReferenceKind.PRIVATE_EVIDENCE_ARTIFACT,
        authorizedReferenceKinds = setOf(FeatureTaskRuntimeCompactReferenceKind.PRIVATE_EVIDENCE_ARTIFACT),
      ),
    )

    val wire = declaration.toArtifactMap()

    assertEquals(mapOf("kind" to "upstream_phase_output", "id" to "implement"), wire["source"])
    assertEquals(mapOf("phase_id" to "implement", "iteration" to 4), wire["producer_iteration"])
    assertEquals(listOf("private_evidence_artifact"), wire["authorized_reference_kinds"])
    assertEquals(false, wire["required"])
    assertEquals(true, wire["allows_private_artifact_reference"])
    assertEquals("private_evidence_artifact", wire["inline_alternative"])
    assertFalse(wire.containsKey("source_ref"))
    assertEquals(declaration, PhaseHandoffProjectionDeclaration.fromArtifactMap(wire, AcceptingFoundationValidator))
  }

  @Test
  fun `measurement mapping is versioned and content free`() {
    val wire = FeatureTaskRuntimeProjectionMeasurement(
      workflowId = "wftr-1",
      consumerPhaseId = "audit",
      projectionContractId = "feature_task_runtime.phase_prose",
      producerIteration = FeatureTaskRuntimeProducerIteration("implement", 2),
      repositoryCheckpointFingerprint = "checkpoint-1",
      projectedUtf8Bytes = 120,
      projectedCollectionItems = 3,
      estimatedTokens = 30,
      privateEvidenceUtf8Bytes = 900,
      deliveredProjectionUtf8Bytes = 120,
    ).toTelemetryMap()

    assertEquals("0.1", wire["contract_version"])
    assertFalse(wire.keys.any { it in setOf("prompt", "payload", "source_body", "diff_body", "receipt") })
  }

  @Test
  fun `shared evidence measurement emits exactly the declared fields for each outcome`() {
    FeatureTaskRuntimeSharedEvidenceOutcome.entries.forEach { outcome ->
      val wire = FeatureTaskRuntimeSharedEvidenceMeasurement(
        workflowId = "wftr-1",
        checkpointFingerprint = "fp-1",
        consumerPhaseId = "audit",
        outcome = outcome,
        fileIndexCount = 2,
        hunkIndexCount = 3,
      ).toTelemetryMap()

      assertEquals(
        setOf(
          "contract_version",
          "workflow_id",
          "checkpoint_fingerprint",
          "consumer_phase_id",
          "outcome",
          "file_index_count",
          "hunk_index_count",
        ),
        wire.keys,
      )
      assertEquals(outcome.wireValue, wire["outcome"])
      assertFalse(wire.keys.any { it in setOf("prompt", "payload", "diff", "path", "file_path") })
    }
  }

  @Test
  fun `producer iteration rejects blank identity and non-positive attempts`() {
    assertFailsWith<IllegalArgumentException> { FeatureTaskRuntimeProducerIteration("", 1) }
    assertFailsWith<IllegalArgumentException> { FeatureTaskRuntimeProducerIteration("plan", 0) }
  }

  @Test
  fun `measurement model exposes counts and classifications but no content bodies`() {
    val propertyNames = FeatureTaskRuntimeProjectionMeasurement::class.java.declaredFields.map { it.name }.toSet()

    listOf("prompt", "payload", "sourceBody", "diffBody", "receipt", "rawOutput", "logs").forEach { forbidden ->
      assertFalse(forbidden in propertyNames, "measurement unexpectedly exposes content field '$forbidden'")
    }
  }

  @Test
  fun `measurement rejects negative counts`() {
    assertFailsWith<IllegalArgumentException> {
      FeatureTaskRuntimeProjectionMeasurement(
        workflowId = "wftr-1",
        consumerPhaseId = "audit",
        projectionContractId = "feature_task_runtime.phase_prose",
        producerIteration = FeatureTaskRuntimeProducerIteration("implement", 1),
        repositoryCheckpointFingerprint = "checkpoint-1",
        projectedUtf8Bytes = -1,
        projectedCollectionItems = 0,
        estimatedTokens = 0,
        privateEvidenceUtf8Bytes = 0,
        deliveredProjectionUtf8Bytes = 0,
      )
    }
  }
}

private object AcceptingFoundationValidator : FeatureTaskRuntimeWireArtifactValidator {
  override fun validate(kind: FeatureTaskRuntimeWireArtifactKind, payload: Any, sourceLabel: String) = Unit
}
