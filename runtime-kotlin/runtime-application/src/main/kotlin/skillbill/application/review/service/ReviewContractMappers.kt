package skillbill.application.review.service
import skillbill.application.review.model.ImportedReviewResult
import skillbill.application.review.model.ReviewFeedbackResult
import skillbill.application.review.model.ReviewPreviewResult
import skillbill.application.review.model.TriageResult
import skillbill.application.review.model.TriageResultKind
import skillbill.application.review.parallel.core.code.review.bundled.decision
import skillbill.application.review.parallel.core.code.review.bundled.finding
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.bundled.service
import skillbill.application.review.parallel.core.code.review.claim.findings
import skillbill.application.review.parallel.core.code.review.end.finding
import skillbill.application.review.parallel.core.code.review.end.reviewRunId
import skillbill.application.review.parallel.core.code.review.inline.findings
import skillbill.application.review.parallel.core.code.review.integration.finding
import skillbill.application.review.parallel.core.code.review.runner.decision
import skillbill.application.review.parallel.core.code.review.runner.findings
import skillbill.application.review.parallel.core.code.review.runner.recorded
import skillbill.application.review.parallel.core.code.review.runner.reviewRunId
import skillbill.application.review.parallel.core.review.decision
import skillbill.application.review.parallel.core.review.service
import skillbill.application.review.parallel.planning.reviewRunId
import skillbill.application.review.parallel.verification.line
import skillbill.application.review.parallel.verification.path
import skillbill.application.review.parallel.verification.reviewRunId
import skillbill.application.review.preparation.decision
import skillbill.application.review.preparation.facts
import skillbill.application.review.preparation.service
import skillbill.application.review.review.facts
import skillbill.application.review.review.stdout
import skillbill.application.review.spec.adjustment
import skillbill.application.review.spec.facts
import skillbill.application.review.spec.finding
import skillbill.application.review.spec.findings
import skillbill.application.review.spec.path
import skillbill.application.review.spec.stdout
import skillbill.application.review.stats.recorded
import skillbill.application.review.verification.decision
import skillbill.application.review.verification.facts
import skillbill.application.review.verification.finding
import skillbill.application.review.verification.findings
import skillbill.application.review.verification.line
import skillbill.application.review.verification.path
import skillbill.application.review.verification.stdout
import skillbill.contracts.JsonPayloadContract
import skillbill.contracts.review.ImportedReviewContract
import skillbill.contracts.review.NumberedFindingContract
import skillbill.contracts.review.ReviewFeedbackContract
import skillbill.contracts.review.ReviewPreviewContract
import skillbill.contracts.review.TriageDecisionContract
import skillbill.contracts.review.TriageListContract
import skillbill.contracts.review.TriageRecordedContract
import skillbill.ports.agentrun.model.AgentRunLaunchFacts
import skillbill.ports.agentrun.model.reviewProcessOutcome
import skillbill.ports.review.model.ReviewProcessOutcome
import skillbill.review.model.ImportedReview
import skillbill.review.model.NumberedFinding
import skillbill.review.model.ReviewFinishedTelemetry
import skillbill.review.model.TriageDecision
import skillbill.ports.telemetry.model.toReviewFinishedTelemetryPayload as toPortReviewFinishedTelemetryPayload

internal enum class ReviewOutputAdmission {
  SUCCESS,
  SCHEMA_REPAIR_ELIGIBLE,
  REJECTED,
}

internal data class ReviewOutputClassification(
  val processOutcome: ReviewProcessOutcome,
  val admission: ReviewOutputAdmission,
)

internal fun classifyReviewOutput(
  facts: AgentRunLaunchFacts,
  resultEnvelopeValid: Boolean,
): ReviewOutputClassification {
  val processOutcome = facts.reviewProcessOutcome()
  return ReviewOutputClassification(
    processOutcome = processOutcome,
    admission = when {
      processOutcome != ReviewProcessOutcome.ZERO_EXIT -> ReviewOutputAdmission.REJECTED
      facts.stdout.isBlank() -> ReviewOutputAdmission.REJECTED
      resultEnvelopeValid -> ReviewOutputAdmission.SUCCESS
      else -> ReviewOutputAdmission.SCHEMA_REPAIR_ELIGIBLE
    },
  )
}

fun ImportedReview.toReviewPreviewResult(): ReviewPreviewResult = ReviewPreviewResult(
  reviewRunId = reviewRunId,
  reviewSessionId = reviewSessionId,
  findingCount = findings.size,
  routedSkill = routedSkill,
  detectedScope = detectedScope,
  detectedStack = detectedStack,
  executionMode = executionMode,
)

fun ImportedReview.toImportedReviewResult(dbPath: String): ImportedReviewResult =
  ImportedReviewResult(dbPath = dbPath, preview = toReviewPreviewResult())

fun ReviewPreviewResult.toReviewPreviewContract(): ReviewPreviewContract = ReviewPreviewContract(
  reviewRunId = reviewRunId,
  reviewSessionId = reviewSessionId,
  findingCount = findingCount,
  routedSkill = routedSkill,
  detectedScope = detectedScope,
  detectedStack = detectedStack,
  executionMode = executionMode?.wireValue,
)

fun ImportedReviewResult.toImportedReviewContract(): ImportedReviewContract =
  ImportedReviewContract(dbPath = dbPath, review = preview.toReviewPreviewContract())

fun NumberedFinding.toNumberedFindingContract(): NumberedFindingContract = NumberedFindingContract(
  number = number,
  findingId = findingId,
  severity = severity,
  confidence = confidence,
  location = location,
  description = description,
  claimVerdict = claimVerdict?.wireValue,
  scopeDisposition = scopeDisposition?.wireValue,
  citations = citations.map { citation -> linkedMapOf("path" to citation.path, "line" to citation.line) },
  severityAdjustment = severityAdjustment?.let { adjustment ->
    linkedMapOf(
      "direction" to adjustment.direction.wireValue,
      "justification" to adjustment.justification,
    )
  },
)

fun TriageDecision.toTriageDecisionContract(): TriageDecisionContract = TriageDecisionContract(
  number = number,
  findingId = findingId,
  outcomeType = outcomeType,
  note = note,
)

fun ReviewFeedbackResult.toReviewFeedbackPayload(): JsonPayloadContract = ReviewFeedbackContract(
  dbPath = dbPath,
  reviewRunId = reviewRunId,
  outcomeType = outcomeType,
  recordedFindings = recordedFindings,
)

fun TriageResult.toTriagePayload(): JsonPayloadContract = when (kind) {
  TriageResultKind.LIST ->
    TriageListContract(
      dbPath = dbPath,
      reviewRunId = reviewRunId,
      findings = findings.map { finding -> finding.toNumberedFindingContract() },
    )
  TriageResultKind.RECORDED ->
    TriageRecordedContract(
      dbPath = dbPath,
      reviewRunId = reviewRunId,
      recorded = recorded.map { decision -> decision.toTriageDecisionContract() },
    )
}

fun ReviewFinishedTelemetry.toReviewFinishedTelemetryPayload(): JsonPayloadContract =
  this.toPortReviewFinishedTelemetryPayload()
