package skillbill.workflow.taskruntime

import skillbill.contracts.review.ReviewVerificationSignalKeys
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.error.InvalidFeatureTaskRuntimeValidationEvidenceSchemaError
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateExecutionEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationGateRunRecord
import skillbill.workflow.taskruntime.model.ValidationGateCacheMode
import skillbill.workflow.taskruntime.model.ValidationGateRunOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureTaskRuntimeValidationGateExecutionEvidenceTest {
  @Test
  fun `malformed gate execution evidence is rejected`() {
    assertFailsWith<InvalidFeatureTaskRuntimeValidationEvidenceSchemaError> {
      FeatureTaskRuntimeValidationGateExecutionEvidence.fromArtifactMap(
        mapOf(ValidationEvidencePayloadKeys.VALIDATION_STATUS to "passed"),
        "validate",
      )
    }
  }

  @Test
  fun `populated multiple-check and zero-work evidence round-trip`() {
    val populated = evidenceArtifact(
      checks = listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
      gateRuns = listOf(
        gateRun(
          cacheMode = ValidationGateCacheMode.CACHE_ELIGIBLE,
          outcome = ValidationGateRunOutcome.FAILED,
          executedWorkUnits = 2,
          executedChecks = listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
        ),
        gateRun(
          cacheMode = ValidationGateCacheMode.FORCED_FULL,
          outcome = ValidationGateRunOutcome.PASSED,
          executedWorkUnits = 2,
          executedChecks = listOf("runtime-engine|compileKotlin", "runtime-engine|test"),
        ),
      ).map { it.toArtifactMap() },
    )
    val decoded = FeatureTaskRuntimeValidationGateExecutionEvidence.fromArtifactMap(populated, "validate")
    assertEquals(listOf("runtime-engine|compileKotlin", "runtime-engine|test"), decoded.checks)
    assertEquals(2, decoded.gateRunCount)
    assertEquals(ValidationGateCacheMode.FORCED_FULL, decoded.gateRuns.last().cacheMode)

    val zeroWork = evidenceArtifact(
      checks = emptyList(),
      gateRuns = listOf(
        gateRun(
          executedWorkUnits = 0,
          executedChecks = emptyList(),
        ).toArtifactMap(),
      ),
    )
    val zeroDecoded = FeatureTaskRuntimeValidationGateExecutionEvidence.fromArtifactMap(zeroWork, "validate")
    assertTrue(zeroDecoded.zeroWork)
    assertTrue(zeroDecoded.evidenceRecorded)
    assertEquals(emptyList(), zeroDecoded.checks)
  }

  @Test
  fun `legacy gate run without executed_checks decodes as absent evidence`() {
    val legacy = evidenceArtifact(
      checks = emptyList(),
      gateRuns = listOf(
        linkedMapOf(
          ValidationEvidencePayloadKeys.DURATION_MS to 1L,
          ValidationEvidencePayloadKeys.OUTCOME to ValidationGateRunOutcome.PASSED.wireValue,
          ValidationEvidencePayloadKeys.CACHE_MODE to ValidationGateCacheMode.CACHE_ELIGIBLE.wireValue,
          ValidationEvidencePayloadKeys.EXECUTED_WORK_UNITS to 1,
        ),
      ),
    )
    val decoded = FeatureTaskRuntimeValidationGateExecutionEvidence.fromArtifactMap(legacy, "validate")
    assertFalse(decoded.evidenceRecorded)
    assertFalse(decoded.zeroWork)
    assertEquals(emptyList(), decoded.checks)
  }

  @Test
  fun `inconsistent aggregate checks are rejected instead of becoming zero-work evidence`() {
    assertFailsWith<InvalidFeatureTaskRuntimeValidationEvidenceSchemaError> {
      FeatureTaskRuntimeValidationGateExecutionEvidence.fromArtifactMap(
        evidenceArtifact(
          checks = emptyList(),
          gateRuns = listOf(
            gateRun(
              executedWorkUnits = 1,
              executedChecks = listOf("runtime-engine|test"),
            ).toArtifactMap(),
          ),
        ),
        "validate",
      )
    }
  }

  @Test
  fun `negative duration and work counts are rejected before artifact encoding`() {
    assertFailsWith<IllegalArgumentException> {
      gateRun(durationMs = -1)
    }
    assertFailsWith<IllegalArgumentException> {
      gateRun(executedWorkUnits = -1)
    }
  }

  @Test
  fun `missing repository checkpoint is rejected at the artifact boundary`() {
    assertFailsWith<InvalidFeatureTaskRuntimeValidationEvidenceSchemaError> {
      FeatureTaskRuntimeValidationGateExecutionEvidence.fromArtifactMap(
        evidenceArtifact(
          checks = emptyList(),
          gateRuns = emptyList(),
        ).toMutableMap().apply {
          remove(ReviewVerificationSignalKeys.REPOSITORY_CHECKPOINT)
        },
        "validate",
      )
    }
  }

  private fun evidenceArtifact(checks: List<String>, gateRuns: List<Any>): Map<String, Any?> = linkedMapOf(
    ValidationEvidencePayloadKeys.VALIDATION_STATUS to "passed",
    ValidationEvidencePayloadKeys.CHECKS to checks,
    ReviewVerificationSignalKeys.REPOSITORY_CHECKPOINT to
      mapOf(ReviewVerificationSignalKeys.REPOSITORY_CHECKPOINT_FINGERPRINT to "checkpoint"),
    ValidationEvidencePayloadKeys.GATE_RUN_COUNT to gateRuns.size,
    ValidationEvidencePayloadKeys.GATE_RUNS to gateRuns,
  )

  private fun gateRun(
    cacheMode: ValidationGateCacheMode = ValidationGateCacheMode.CACHE_ELIGIBLE,
    outcome: ValidationGateRunOutcome = ValidationGateRunOutcome.PASSED,
    durationMs: Long = 1,
    executedWorkUnits: Int = 1,
    executedChecks: List<String> = listOf("runtime-engine|compileKotlin"),
  ): FeatureTaskRuntimeValidationGateRunRecord = FeatureTaskRuntimeValidationGateRunRecord(
    durationMs = durationMs,
    outcome = outcome,
    cacheMode = cacheMode,
    executedWorkUnits = executedWorkUnits,
    executedChecks = executedChecks,
  )
}
