package skillbill.engine.featuretask.lifecycle.core
import skillbill.workflow.goal.model.GoalSubtaskReviewCompactFinding
import skillbill.workflow.taskruntime.artifact.decodeFindingVerificationDispositionFromArtifact
import skillbill.workflow.taskruntime.model.repair.task.FeatureTaskRuntimeRepairOutcome
import skillbill.workflow.taskruntime.model.repair.task.FeatureTaskRuntimeRepairReceipt
import skillbill.workflow.taskruntime.model.repair.task.omittedCarriedFindings
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeFindingVerificationDisposition
import skillbill.workflow.taskruntime.model.validation.validateDispositionCoverage
import kotlin.test.assertTrue
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
    requireNotNull(decodeFindingVerificationDispositionFromArtifact(entry, "finding_dispositions[$index]"))
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
