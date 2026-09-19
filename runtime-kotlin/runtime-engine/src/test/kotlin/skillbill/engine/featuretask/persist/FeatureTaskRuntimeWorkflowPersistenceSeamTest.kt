package skillbill.engine.featuretask.persist
import org.junit.jupiter.api.Test
import skillbill.contracts.JsonCodec
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.artifact.decodeGoalContinuationArtifactFromArtifact
import skillbill.workflow.taskruntime.artifact.decodeHandoffEnvelopeFromArtifact
import skillbill.workflow.taskruntime.artifact.decodeValidationGateProgressFromArtifact
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeHandoffEnvelope
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.FeatureTaskRuntimeGoalContinuationArtifact
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_OUTCOME_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.validation.FEATURE_TASK_RUNTIME_VALIDATION_GATE_PROGRESS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationGateProgress
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
class FeatureTaskRuntimeWorkflowPersistenceSeamTest {
  @Test
  fun `artifacts round trip preserves bytes`() {
    val artifactsJson = """{"progress_event":{"summary":"a"},"goal_continuation_outcome":null}"""
    val decoded = FeatureTaskRuntimeWorkflowPersistence.artifactsFromJson(artifactsJson)!!
    val repersisted = JsonCodec.mapToJsonString(decoded)
    assertEquals(artifactsJson, repersisted)
  }

  @Test
  fun `supported artifact families decode after owner round trip`() {
    assertFamilyRoundTrip(
      key = FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY,
      expected = FeatureTaskRuntimeGoalContinuationArtifact(
        issueKey = "SKILL-352",
        subtaskId = 2,
        suppressPr = false,
        goalBranch = "feat/skill-352",
        codeReviewMode = CodeReviewExecutionMode.AUTO,
      ),
      wire = FeatureTaskRuntimeGoalContinuationArtifact(
        issueKey = "SKILL-352",
        subtaskId = 2,
        suppressPr = false,
        goalBranch = "feat/skill-352",
        codeReviewMode = CodeReviewExecutionMode.AUTO,
      ).asWorkflowArtifactEntry(),
      decode = { raw -> requireNotNull(decodeGoalContinuationArtifactFromArtifact(raw)) },
    )
    assertFamilyRoundTrip(
      key = FEATURE_TASK_RUNTIME_VALIDATION_GATE_PROGRESS_ARTIFACT_KEY,
      expected = FeatureTaskRuntimeValidationGateProgress(
        gateRunCount = 0,
        gateRuns = emptyList(),
      ),
      wire = FeatureTaskRuntimeValidationGateProgress(
        gateRunCount = 0,
        gateRuns = emptyList(),
      ).asWorkflowArtifactEntry(),
      decode = { raw -> requireNotNull(decodeValidationGateProgressFromArtifact(raw)) },
    )
    assertFamilyRoundTrip(
      key = "handoff_envelope",
      expected = FeatureTaskRuntimeHandoffEnvelope(consumerPhaseId = "implement"),
      wire = FeatureTaskRuntimeHandoffEnvelope(consumerPhaseId = "implement").asWorkflowArtifactEntry(),
      decode = { raw -> requireNotNull(decodeHandoffEnvelopeFromArtifact(raw)) },
    )
  }

  @Test
  fun `supported artifact family snapshots keep their durable bytes`() {
    listOf(
      """{"$FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY":{"plan":{"phase_id":"plan","status":"completed",""" +
        """"attempt_count":1}}}""",
      """{"$FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY":[{"action":"complete","sequence_number":0,""" +
        """"timestamp":"2026-09-17T12:00:00Z","phase_id":"plan","attempt_count":1}]}""",
      """{"$FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_OUTCOME_ARTIFACT_KEY":null}""",
    ).forEach { fixture ->
      val restored = FeatureTaskRuntimeWorkflowPersistence.artifactsFromJson(fixture)

      assertEquals(fixture, JsonCodec.mapToJsonString(requireNotNull(restored)))
    }
  }

  private fun assertFamilyRoundTrip(key: String, expected: Any, wire: Any, decode: (Map<String, Any?>) -> Any) {
    val valueMap = assertNotNull(JsonCodec.anyToStringAnyMap(wire))
    val fixture = JsonCodec.mapToJsonString(mapOf(key to valueMap))
    val restored = requireNotNull(FeatureTaskRuntimeWorkflowPersistence.artifactsFromJson(fixture))
    val restoredMap = assertNotNull(JsonCodec.anyToStringAnyMap(restored[key]))

    assertEquals(fixture, JsonCodec.mapToJsonString(restored))
    assertEquals(valueMap, restoredMap)
    assertEquals(expected, decode(restoredMap))
  }

  @Test
  fun `phase recorder role interfaces and wire mapping are removed`() {
    val featuretaskDir = Path.of(
      "src/main/kotlin/skillbill/engine/featuretask",
    )
    listOf(
      "FeatureTaskRuntimePhaseRecorderApis.kt",
      "FeatureTaskRuntimePhaseRecorderExtendedApis.kt",
      "FeatureTaskRuntimeWireMapping.kt",
    ).forEach { name ->
      assertFalse(featuretaskDir.resolve(name).toFile().exists(), "expected $name to be removed")
    }
  }
}
