package skillbill.engine.featuretask

import skillbill.workflow.goal.model.GoalSubtaskReviewCompactFinding
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFindingVerificationDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepairOutcome
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepairReceipt
import skillbill.workflow.taskruntime.model.omittedCarriedFindings
import skillbill.workflow.taskruntime.model.validateDispositionCoverage
import kotlin.test.assertTrue

import skillbill.workflow.taskruntime.asCheckpointIdentitiesArtifactEntry
import skillbill.workflow.taskruntime.asTelemetryPayload
import skillbill.workflow.taskruntime.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.decodeFindingVerificationDispositionFromArtifact
import skillbill.workflow.taskruntime.decodeImplementationAttemptFromArtifact
import skillbill.workflow.taskruntime.decodePhaseRecordFromArtifact
import skillbill.workflow.taskruntime.decodeValidationGateExecutionEvidenceFromArtifact
import skillbill.workflow.taskruntime.decodeValidationGateProgressFromArtifact
import skillbill.workflow.taskruntime.envelopeWireMap
import skillbill.workflow.taskruntime.phaseRecordsFromWorkflowArtifacts
import skillbill.workflow.taskruntime.toWorkflowArtifactMap
object FeatureTaskRuntimeCensusCoverageTestSupport {
  fun verifyDisposition(findingId: String, disposition: String = "verified"): Map<String, String> = mapOf(
    "finding_id" to findingId,
    "disposition" to disposition,
  )

  fun repairEntry(
    findingId: String,
    outcome: String = FeatureTaskRuntimeRepairOutcome.ADDRESSED.wireValue,
  ): Map<String, String> = mapOf(
    "finding_id" to findingId,
    "outcome" to outcome,
  )

  fun parseVerifyDispositions(
    entries: List<Map<String, String>>,
  ): List<FeatureTaskRuntimeFindingVerificationDisposition> = entries.mapIndexed { index, entry ->
    decodeFindingVerificationDispositionFromArtifact(entry, "finding_dispositions[$index]")!!
  }

  fun assertVerifyCoverageContains(
    dispositions: List<FeatureTaskRuntimeFindingVerificationDisposition>,
    reviewFindingIds: Set<String>,
    fragment: String,
  ) {
    val reason = validateDispositionCoverage(dispositions, reviewFindingIds)
    assertTrue(reason?.contains(fragment) == true, "expected '$fragment' in $reason")
  }

  fun assertRepairOmits(
    receipt: FeatureTaskRuntimeRepairReceipt,
    carried: List<GoalSubtaskReviewCompactFinding>,
    expectedOmittedIds: Set<String>,
  ) {
    val omittedIds = receipt.omittedCarriedFindings(carried).mapNotNull { it.findingId }.toSet()
    assertTrue(omittedIds == expectedOmittedIds, "expected omitted $expectedOmittedIds but got $omittedIds")
  }
}
