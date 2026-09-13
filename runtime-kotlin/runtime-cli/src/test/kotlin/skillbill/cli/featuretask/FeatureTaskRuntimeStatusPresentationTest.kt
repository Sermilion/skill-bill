package skillbill.cli.featuretask

import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStatus
import skillbill.engine.featuretask.model.FeatureTaskRuntimeStatusProjection
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateExecutionEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateRunRecord
import skillbill.workflow.taskruntime.model.ValidationGateCacheMode
import skillbill.workflow.taskruntime.model.ValidationGateRunOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeatureTaskRuntimeStatusPresentationTest {
  @Test
  fun `runtime status cli map preserves settled gate checks and work evidence`() {
    val evidence = FeatureTaskRuntimeValidationGateExecutionEvidence.fromGateMeasurements(
      measurements = listOf(
        FeatureTaskRuntimeValidationGateRunRecord(
          durationMs = 1,
          outcome = ValidationGateRunOutcome.PASSED,
          cacheMode = ValidationGateCacheMode.FORCED_FULL,
          executedWorkUnits = 2,
          executedChecks = listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
        ),
      ),
    )
    val projection = FeatureTaskRuntimeStatusProjection(
      workflowId = "workflow-1",
      featureSize = "MEDIUM",
      phases = listOf(
        FeatureTaskRuntimePhaseStatus(
          phaseId = "validate",
          status = "completed",
          attemptCount = 1,
          resolvedAgentId = "claude",
          finished = true,
        ),
      ),
      completeCount = 1,
      pendingCount = 0,
      blockedCount = 0,
      currentPhaseId = "validate",
      validationGateExecutionEvidence = evidence,
    )

    val status = projection.toRuntimeStatusCliMap("workflow-1")
    assertEquals(
      listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
      status[ValidationEvidencePayloadKeys.CHECKS],
    )
    assertEquals(1, status[ValidationEvidencePayloadKeys.GATE_RUN_COUNT])
    val run = (status[ValidationEvidencePayloadKeys.GATE_RUNS] as List<*>).single() as Map<*, *>
    assertEquals(2, run[ValidationEvidencePayloadKeys.EXECUTED_WORK_UNITS])
    assertEquals(
      ValidationGateCacheMode.FORCED_FULL.wireValue,
      run[ValidationEvidencePayloadKeys.CACHE_MODE],
    )
    assertEquals(
      ValidationGateRunOutcome.PASSED.wireValue,
      run[ValidationEvidencePayloadKeys.OUTCOME],
    )
    val text = runtimeStatusText(status)
    assertTrue(text.contains("validation_gate_checks: runtime-engine|compileKotlin,runtime-engine|test"))
    assertTrue(text.contains("executed_work_units=2"))
    assertTrue(text.contains("checks=runtime-engine|compileKotlin,runtime-engine|test"))
  }
}
